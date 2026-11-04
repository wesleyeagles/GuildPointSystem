package com.guild.app.translate.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Regras de texto do tradutor do chat: normalização (chave do dicionário) e troca de termos do glossário. */
public final class ChatText {
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern REPEATED_PUNCTUATION = Pattern.compile("([\\p{P}\\p{S}])\\1+");
    private static final Pattern CYRILLIC = Pattern.compile("\\p{IsCyrillic}");

    private ChatText() {}

    /** "  Рёс!!!  " -> "рес!": minúsculas, ё = е, espaços e pontuação repetida reduzidos. */
    public static String normalize(String text) {
        if (text == null) return "";
        String t = text.toLowerCase(Locale.ROOT).replace('ё', 'е');
        t = SPACES.matcher(t).replaceAll(" ").strip();
        return REPEATED_PUNCTUATION.matcher(t).replaceAll("$1");
    }

    public static boolean hasCyrillic(String text) { return CYRILLIC.matcher(text).find(); }

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
    }
}
