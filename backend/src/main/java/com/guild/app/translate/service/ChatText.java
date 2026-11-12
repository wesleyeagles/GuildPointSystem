package com.guild.app.translate.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Regras de texto do tradutor do chat (russo e indonésio): normalização (chave do dicionário) e troca de termos do glossário. */
public final class ChatText {
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern REPEATED_PUNCTUATION = Pattern.compile("([\\p{P}\\p{S}])\\1+");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");
    private static final Pattern LETTER = Pattern.compile("\\p{L}");

    private ChatText() {}

    /** "  Рёс!!!  " -> "рес!": minúsculas, ё = е, espaços e pontuação repetida reduzidos. */
    public static String normalize(String text) {
        if (text == null) return "";
        String t = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        t = SPACES.matcher(t).replaceAll(" ").strip();
        return REPEATED_PUNCTUATION.matcher(t).replaceAll("$1");
    }

    public static boolean hasCyrillic(String text) { return CYRILLIC.matcher(text).find(); }

    /**
     * Indonésio (DLL 1.40.0): escrito com letras comuns, então é reconhecido pelas palavras. "Fortes" não existem
     * em inglês nem em português; "fracas" também aparecem fora do indonésio ("di", "mau", "Dan", "tp" de teleporte).
     * Conta como indonésio com 2 fortes, 1 forte + 1 fraca, ou 1 forte numa mensagem de até 3 palavras.
     * A mesma regra fica na DLL (LooksIndonesian em chat_translate_model.hpp), que decide o que mandar.
     */
    static final Set<String> INDONESIAN_STRONG = Set.of(
        "yang", "tidak", "tdk", "gak", "nggak", "ngga", "enggak", "gk", "aku", "kamu", "sudah", "udah", "udh",
        "belum", "blm", "bisa", "jual", "beli", "dong", "sih", "nih", "deh", "kok", "aja", "saja", "gimana",
        "bagaimana", "kenapa", "napa", "berapa", "brp", "siapa", "kita", "kami", "mereka", "juga", "jg", "banget",
        "bgt", "gw", "gue", "gua", "ayo", "yuk", "tunggu", "makasih", "terima", "kasih", "mantap", "sini", "dulu",
        "nanti", "punya", "pakai", "pake", "kalau", "kalo", "karena", "tapi", "atau", "bukan", "jangan",
        "cuma", "lagi", "ini", "itu", "buat", "untuk", "utk", "dari", "ada", "sama", "dimana");
    static final Set<String> INDONESIAN_WEAK = Set.of(
        "dan", "di", "ke", "lu", "lo", "ga", "mau", "ya", "bang", "hari", "mati", "sana", "mana", "tp");
    private static final Pattern WORD = Pattern.compile("[a-z][a-z0-9]*");

    public static boolean looksIndonesian(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) >= 0x80) return false;  // acento ou cirílico
        int strong = 0, weak = 0, words = 0;
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            words++;
            if (INDONESIAN_STRONG.contains(m.group())) strong++;
            else if (INDONESIAN_WEAK.contains(m.group())) weak++;
        }
        return strong >= 2 || (strong >= 1 && weak >= 1) || (strong >= 1 && words <= 3);
    }

    /** Termos do glossário (já normalizados) como palavras inteiras; os mais longos primeiro ("кинь инв" antes de "инв"). */
    public record Glossary(Pattern pattern, Map<String, String> terms) {
        public static final Glossary EMPTY = new Glossary(null, Map.of());

        public static Glossary of(Map<String, String> terms) {
            if (terms.isEmpty()) return EMPTY;
            List<String> sources = terms.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList();
            String alternatives = sources.stream().map(Pattern::quote).collect(Collectors.joining("|"));
            Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])(?:" + alternatives + ")(?![\\p{L}\\p{N}])");
            return new Glossary(pattern, Map.copyOf(terms));
        }

        /** Texto normalizado com cada termo trocado pela tradução do glossário. */
        public String apply(String normalized) {
            if (pattern == null) return normalized;
            Matcher m = pattern.matcher(normalized);
            StringBuilder out = new StringBuilder();
            while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(terms.get(m.group())));
            m.appendTail(out);
            return out.toString();
        }

        /**
         * Texto montado só com termos do glossário: cada palavra do texto precisa estar num termo (números e
         * pontuação podem ficar); se sobrar alguma palavra, null (vai para o dicionário ou para a API).
         */
        public String applyComplete(String normalized) {
            if (pattern == null) return null;
            Matcher m = pattern.matcher(normalized);
            StringBuilder out = new StringBuilder();
            int last = 0;
            boolean any = false;
            while (m.find()) {
                if (LETTER.matcher(normalized.substring(last, m.start())).find()) return null;
                m.appendReplacement(out, Matcher.quoteReplacement(terms.get(m.group())));
                last = m.end();
                any = true;
            }
            if (!any || LETTER.matcher(normalized.substring(last)).find()) return null;
            m.appendTail(out);
            return out.toString();
        }
    }
}
