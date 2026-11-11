package com.guild.app.market.service;

import java.util.List;
import java.util.Optional;

/** De onde vêm os anúncios do market (a Cerberus de verdade; nos testes, um dublê). */
public interface MarketSource {
    /** Todos os anúncios do código (até o limite de páginas); vazio = market fora do ar ou sem login agora. */
    Optional<List<MarketPage.Listing>> listings(String code);
}
