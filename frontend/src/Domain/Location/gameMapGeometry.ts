// Gerado de gameclient04102026 (patch de 24/10/2026), somente leitura do cliente:
//  - imagem: textura do .spr do registro do mapa (+0xE4), em /game-maps;
//  - largura/altura: como a janela do mapa do jogo estica a imagem (registro +0x1A8/+0x1AC);
//  - limites: caixa do no raiz do .bsp do mapa (a mesma que a janela usa, 0x6F98DB no cliente).
// box null = .bsp criptografado (Elan novo): os limites vem da escala que a DLL envia (useGameMapViews).
// Ponto na imagem: px = (x - minX) * width / (maxX - minX); py = (maxZ - z) * height / (maxZ - minZ).

export interface GameMapGeometry {
  name: string
  image: string
  width: number
  height: number
  box: { minX: number; minZ: number; maxX: number; maxZ: number } | null
}

export const GAME_MAP_GEOMETRY: Record<number, GameMapGeometry> = {
  0: { name: "Bellato HQ", image: '/game-maps/Bel_Field.webp', width: 439, height: 646, box: { minX: -9886, minZ: -11177, maxX: 14999, maxZ: 25388 } },
  1: { name: "Cora HQ", image: '/game-maps/Cor_Field.webp', width: 454, height: 499, box: { minX: -13570, minZ: -11420, maxX: 14006, maxZ: 18866 } },
  2: { name: "Crag Mind Field", image: '/game-maps/Resource_Field.webp', width: 410, height: 345, box: { minX: -15421, minZ: -9238, maxX: 10791, maxZ: 12821 } },
  3: { name: "Accretia HQ", image: '/game-maps/Acc_Field.webp', width: 410, height: 302, box: { minX: -14749, minZ: -10932, maxX: 15422, maxZ: 11311 } },
  4: { name: "Solus", image: '/game-maps/Nbs1_Field.webp', width: 400, height: 363, box: { minX: -12961, minZ: -12620, maxX: 12768, maxZ: 10787 } },
  5: { name: "Anacaade", image: '/game-maps/Nbs2_Field.webp', width: 711, height: 745, box: { minX: -21203, minZ: -14690, maxX: 12474, maxZ: 20590 } },
  6: { name: "Haram", image: '/game-maps/Ncs1_Field.webp', width: 666, height: 405, box: { minX: -23905, minZ: -15582, maxX: 27549, maxZ: 15667 } },
  7: { name: "Numerus", image: '/game-maps/Ncs2_Field.webp', width: 432, height: 220, box: { minX: -13446, minZ: -1300, maxX: 13417, maxZ: 12347 } },
  8: { name: "Armory213", image: '/game-maps/Nas1_Field.webp', width: 407, height: 388, box: { minX: -10023, minZ: -10744, maxX: 10877, maxZ: 9188 } },
  9: { name: "Armory117", image: '/game-maps/Nas2_Field.webp', width: 417, height: 347, box: { minX: -12622, minZ: -11572, maxX: 13707, maxZ: 10345 } },
  10: { name: "Ether of Heaven", image: '/game-maps/Pla01_Field.webp', width: 471, height: 377, box: { minX: -14167, minZ: -11970, maxX: 16868, maxZ: 12855 } },
  11: { name: "Sette Desert", image: '/game-maps/Sette_Field.webp', width: 337, height: 400, box: { minX: -5198, minZ: -6812, maxX: 5845, maxZ: 6339 } },
  12: { name: "Coldron Volcanic Area", image: '/game-maps/Cauldron01_Field.webp', width: 627, height: 495, box: { minX: -12420, minZ: -6911, maxX: 6160, maxZ: 7738 } },
  13: { name: "Elan Plateau", image: '/game-maps/Elan_Field.webp', width: 1405, height: 1433, box: { minX: -28844, minZ: -27624, maxX: 27951, maxZ: 30298 } },
  15: { name: "Cartella Ship", image: '/game-maps/Trns01_Field.webp', width: 400, height: 400, box: { minX: -446, minZ: -368, maxX: 446, maxZ: 308 } },
  24: { name: "Outcast's Land", image: '/game-maps/Exile_Land.webp', width: 400, height: 400, box: { minX: -10000, minZ: -10000, maxX: 10000, maxZ: 10000 } },
  25: { name: "Beast's Mountain", image: '/game-maps/Mountain_Beast.webp', width: 400, height: 400, box: { minX: -10000, minZ: -10000, maxX: 10000, maxZ: 10000 } },
  26: { name: "Cartella Bionic Laboratory", image: '/game-maps/Medicallab.webp', width: 400, height: 400, box: { minX: -9731, minZ: -2383, maxX: 1439, maxZ: 4155 } },
  27: { name: "AUTUMN LANDS", image: '/game-maps/Cora.webp', width: 400, height: 400, box: { minX: -7979, minZ: -11626, maxX: 5923, maxZ: 6685 } },
  28: { name: "Chaos Hole", image: '/game-maps/Dungeon03.webp', width: 400, height: 400, box: { minX: -5817, minZ: -5844, maxX: 5844, maxZ: 5640 } },
  29: { name: "Cartella Bionics laboratory 2nd basement", image: '/game-maps/Medicallab2.webp', width: 400, height: 400, box: { minX: -9731, minZ: -2383, maxX: 1439, maxZ: 4155 } },
  30: { name: "Instant Dungeon", image: '/game-maps/Dungeon04.webp', width: 400, height: 400, box: { minX: -7979, minZ: -11626, maxX: 5923, maxZ: 6685 } },
  31: { name: "Outcasts' Castle", image: '/game-maps/mustion_disel.webp', width: 503, height: 501, box: { minX: -5834, minZ: -4939, maxX: 4269, maxZ: 4647 } },
  32: { name: "Hunters Desert", image: '/game-maps/Sette_Field.webp', width: 337, height: 400, box: { minX: -5198, minZ: -6812, maxX: 5845, maxZ: 6339 } },
  33: { name: "SUMMER RESIDENCE", image: '/game-maps/Dungeon03.webp', width: 400, height: 400, box: { minX: -5817, minZ: -5844, maxX: 5844, maxZ: 5640 } },
  34: { name: "Hell Forge", image: '/game-maps/Cauldron01_Field.webp', width: 627, height: 495, box: { minX: -12420, minZ: -6911, maxX: 6160, maxZ: 7738 } },
  35: { name: "Hall of Lost Treasures", image: '/game-maps/mustion_disel.webp', width: 503, height: 501, box: { minX: -5834, minZ: -4939, maxX: 4269, maxZ: 4647 } },
  36: { name: "Training camp", image: '/game-maps/Pla01_Field.webp', width: 471, height: 377, box: { minX: -14167, minZ: -11970, maxX: 16868, maxZ: 12855 } },
  37: { name: "Desolation Wasteland", image: '/game-maps/Forrest03A.webp', width: 680, height: 678, box: { minX: -20658, minZ: -11170, maxX: 12880, maxZ: 8144 } },
  38: { name: "Outcasts' Castle", image: '/game-maps/mustion_disel.webp', width: 503, height: 501, box: { minX: -5834, minZ: -4939, maxX: 4269, maxZ: 4647 } },
  39: { name: "Beast's Mountain", image: '/game-maps/Mountain_Beast.webp', width: 400, height: 400, box: { minX: -10000, minZ: -10000, maxX: 10000, maxZ: 10000 } },
  40: { name: "Beast's Mountain", image: '/game-maps/Elan_Field.webp', width: 1405, height: 1433, box: { minX: -28844, minZ: -27624, maxX: 27951, maxZ: 30298 } },
  41: { name: "Cartella Bionics laboratory 2nd basement", image: '/game-maps/Medicallab2.webp', width: 400, height: 400, box: { minX: -9731, minZ: -2383, maxX: 1439, maxZ: 4155 } },
  42: { name: "Beast's Mountain", image: '/game-maps/Elan_Field.webp', width: 1405, height: 1433, box: { minX: -28844, minZ: -27624, maxX: 27951, maxZ: 30298 } },
  43: { name: "Hell Forge", image: '/game-maps/Cauldron01_Field.webp', width: 627, height: 495, box: { minX: -12420, minZ: -6911, maxX: 6160, maxZ: 7738 } },
  44: { name: "Hell Forge", image: '/game-maps/Cauldron01_Field.webp', width: 627, height: 495, box: { minX: -12420, minZ: -6911, maxX: 6160, maxZ: 7738 } },
  45: { name: "Training camp 2", image: '/game-maps/Pla01_Field.webp', width: 471, height: 377, box: { minX: -14167, minZ: -11970, maxX: 16868, maxZ: 12855 } },
  46: { name: "Training camp 3", image: '/game-maps/Pla01_Field.webp', width: 471, height: 377, box: { minX: -14167, minZ: -11970, maxX: 16868, maxZ: 12855 } },
  47: { name: "Outcasts' Castle", image: '/game-maps/mustion_disel.webp', width: 503, height: 501, box: { minX: -5834, minZ: -4939, maxX: 4269, maxZ: 4647 } },
  48: { name: "Hell Forge", image: '/game-maps/Cauldron01_Field.webp', width: 627, height: 495, box: { minX: -12420, minZ: -6911, maxX: 6160, maxZ: 7738 } },
  50: { name: "DebuffLevelingArea", image: '/game-maps/Dungeon03.webp', width: 400, height: 400, box: { minX: -5817, minZ: -5844, maxX: 5844, maxZ: 5640 } },
  51: { name: "HoraDarkHall", image: '/game-maps/Dungeon03.webp', width: 400, height: 400, box: { minX: -5817, minZ: -5844, maxX: 5844, maxZ: 5640 } },
  52: { name: "HoraDarkHall", image: '/game-maps/Dungeon03.webp', width: 400, height: 400, box: { minX: -5817, minZ: -5844, maxX: 5844, maxZ: 5640 } },
  53: { name: "Elan Plateau", image: '/game-maps/ElanNew_Field.webp', width: 1380, height: 1433, box: null },
  54: { name: "Cartella Bionic Laboratory", image: '/game-maps/Medicallab4.webp', width: 439, height: 200, box: { minX: -13467, minZ: -3223, maxX: 3041, maxZ: 3981 } },
}
