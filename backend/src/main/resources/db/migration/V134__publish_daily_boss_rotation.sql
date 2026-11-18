-- Publish the approved daily rotation (times in America/Sao_Paulo).
UPDATE boss_rotation
SET slots = $rotation$[
  {
    "time": "02:00",
    "qg": [
      {"guild": "Yonkou", "bosses": ["Warbeast"]},
      {"guild": "Devotion", "bosses": ["Splinter"]},
      {"guild": "Quit", "bosses": ["Ratmoth", "Beach (Vafer)"]}
    ],
    "outpost": [
      {"guild": "Devotion", "bosses": ["Blood King Twizer", "Brutal Rex (Mantis)"]},
      {"guild": "Energy", "bosses": ["Rex Cannibal (Rhino)"]},
      {"guild": "Yonkou", "bosses": ["RashVafer Luther", "Twizer Rex"]},
      {"guild": "Blacklist", "bosses": ["Heavy Anabola", "Great Lava"]},
      {"guild": "Quit", "bosses": ["Blood Vafer Rex"]}
    ]
  },
  {
    "time": "06:00",
    "qg": [
      {"guild": "Devotion", "bosses": ["Warbeast", "Splinter"]},
      {"guild": "Quit", "bosses": ["Ratmoth", "Beach (Vafer)"]}
    ],
    "outpost": []
  },
  {
    "time": "10:00",
    "qg": [
      {"guild": "Yonkou", "bosses": ["Warbeast", "Splinter"]},
      {"guild": "Devotion", "bosses": ["Ratmoth", "Beach (Vafer)"]}
    ],
    "outpost": [
      {"guild": "Energy", "bosses": ["Blood King Twizer", "Rex Cannibal (Rhino)", "Twizer Rex"]},
      {"guild": "Espada", "bosses": ["RashVafer Luther", "Heavy Anabola", "Great Lava"]},
      {"guild": "Devotion", "bosses": ["Brutal Rex (Mantis)"]},
      {"guild": "Yonkou", "bosses": ["Blood Vafer Rex"]}
    ]
  },
  {
    "time": "14:00",
    "qg": [
      {"guild": "Yonkou", "bosses": ["Warbeast", "Splinter"]},
      {"guild": "Blacklist", "bosses": ["Ratmoth", "Beach (Vafer)"]}
    ],
    "outpost": []
  },
  {
    "time": "18:00",
    "qg": [
      {"guild": "Yonkou", "bosses": ["Warbeast"]},
      {"guild": "Quit", "bosses": ["Splinter"]},
      {"guild": "Devotion", "bosses": ["Ratmoth"]},
      {"guild": "Blacklist", "bosses": ["Beach (Vafer)"]}
    ],
    "outpost": [
      {"guild": "Espada", "bosses": ["Blood King Twizer", "RashVafer Luther"]},
      {"guild": "Energy", "bosses": ["Rex Cannibal (Rhino)", "Twizer Rex"]},
      {"guild": "Blacklist", "bosses": ["Brutal Rex (Mantis)"]},
      {"guild": "Quit", "bosses": ["Heavy Anabola", "Great Lava", "Blood Vafer Rex"]}
    ]
  },
  {
    "time": "22:00",
    "qg": [
      {"guild": "Quit", "bosses": ["Warbeast"]},
      {"guild": "Blacklist", "bosses": ["Splinter", "Ratmoth", "Beach (Vafer)"]}
    ],
    "outpost": []
  }
]$rotation$::jsonb,
    version = version + 1,
    updated_at = now(),
    updated_by = NULL
WHERE id = 1;
