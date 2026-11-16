package com.guild.app.pbstatus;

import java.util.Optional;

/** HTML da pagina de status de PB da kbase; vazio = nao conseguiu ler agora. */
public interface PbStatusSource {
    Optional<String> page();
}
