# MCreatorMCP Extended — Native MCreator AI Plugin & Embedded Project Intelligence Engine (v4.0.0)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![MCreator](https://img.shields.io/badge/MCreator-2020.1%20--%202026.x%2B-orange.svg)](https://mcreator.net/)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%2B-red.svg)](https://adoptium.net/)
[![MCP Tools](https://img.shields.io/badge/MCP%20Tools-9%20High--Level%20%2B%20170%20Core-brightgreen.svg)](https://modelcontextprotocol.io/)

> **Original Author:** `modpotato`  
> **Forked, Architected & Maintained by:** `cmmdx256`

A next-generation **Native MCreator AI Plugin & Model Context Protocol (MCP)** server integration for [MCreator](https://mcreator.net/). Running natively inside MCreator's JVM and UI lifecycle, it transforms MCreator into an AI-aware development platform equipped with real-time **Live UI & Editor Context Awareness**, an in-memory **Semantic Project Graph**, an **Incremental Change Indexer**, a **Multi-Stage Validator**, an **Atomic Transaction & Snapshot Rollback Engine**, and **9 High-Level Intelligence Tools** while maintaining 100% backwards compatibility with **170 low-level core capability tools**.

---

# Spoilers: 
<img width="1279" height="781" alt="image" src="https://github.com/user-attachments/assets/5f2b6447-7af5-4c31-a057-499822b2b326" />


---

## 📜 Attribution, Licensing & Project History

### 🤝 Project Lineage & Attribution
* **Original Foundation:** Based on [`modpotato/MCreatorMCP`](https://github.com/modpotato/MCreatorMCP), originally created by **`modpotato`**.
* **Extended Implementation:** Forked, completely re-architected, and maintained by **`cmmdx256`**. This extended version introduces the Embedded Project Intelligence Engine, Live MCreator UI Context Provider, Swing EDT UI Synchronizer, in-memory Semantic Project Graph, and a massive expansion from basic tools to 170+ native capabilities and 9 High-Level task execution tools.

### ⚖️ License Transition & Compliance (MIT → GPL-3.0)
The original foundation was initially published under the **MIT License**. Because MCreator itself is open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**, and this extended plugin integrates deeply and directly with MCreator's core Java runtime classes, UI event lifecycle, and internal workspace APIs:

1. **Licensing:** This project is officially distributed under the terms of the **GNU General Public License v3.0 (GPL-3.0)** to maintain 100% legal compatibility and full compliance with MCreator's upstream open-source license.
2. **Original Notice Preservation:** In accordance with the MIT License terms, the original copyright notices and permissions from `modpotato` and Pylo are fully preserved in the source repository.
3. **Open Source Guarantee:** All derivative works, enhancements, and plugin extensions remain free and open-source under GPL-3.0.

---

## 🌟 Key Architecture & Capabilities

```text
                         AI CLIENT (Claude Desktop / Cursor / Antigravity)
                                            │
                                            │ MCP Protocol (JSON-RPC 2.0 via HTTP/SSE/Stdio)
                                            ▼
                  ┌───────────────────────────────────────────────────┐
                  │          MCreatorMCP High-Level MCP Router         │
                  │   (9 Public High-Level Tools / Multi-Mode Router)  │
                  └─────────────────────────┬─────────────────────────┘
                                            │
                                            ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                           MCREATOR NATIVE RUNTIME & JVM                                     │
│                                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────────────────────┐  │
│  │                    MCREATOR LIVE CONTEXT PROVIDER (LiveContextProvider)               │  │
│  │  • Active Editor Tab & Open ModElementGUI (`mcreator.getTabs().getCurrentTab()`)      │  │
│  │  • Currently Selected Elements in Workspace Table (`WorkspacePanel.list`)             │  │
│  │  • Active Folder Navigation (`FolderElement`) & Open Tabs                             │  │
│  │  • Live Gradle Console & Build Process Stream                                         │  │
│  └────────────────────────────────────────┬──────────────────────────────────────────────┘  │
│                                           │                                                 │
│  ┌────────────────────────────────────────▼──────────────────────────────────────────────┐  │
│  │               EMBEDDED PROJECT INTELLIGENCE ENGINE (v4.0.0)                           │  │
│  │  • In-Memory Project Model (`ProjectModel`)                                            │  │
│  │  • Bi-directional Semantic Dependency Graph (`SemanticProjectGraph`)                  │  │
│  │  • Incremental O(1) Indexer & Event Tracker (`IncrementalChangeTracker`)              │  │
│  │  • Multi-Stage Pre/Post Validator (`ValidationEngine` - FreeMarker / TPS Lag Loops)    │  │
│  │  • Atomic Transaction & Auto-Rollback Engine (`TransactionManager` / `Snapshot`)      │  │
│  │  • Natural Language Intent Planner & Task Engine (`TaskEngine`)                       │  │
│  └────────────────────────────────────────┬──────────────────────────────────────────────┘  │
│                                           │                                                 │
│  ┌────────────────────────────────────────▼──────────────────────────────────────────────┐  │
│  │             INTERNAL CAPABILITY LAYER (170 Core Capabilities via Registry)            │  │
│  │  • AST Code Injection • Blockly XML DOM Editor • Normal Map Generator • Conflict DB    │  │
│  └────────────────────────────────────────┬──────────────────────────────────────────────┘  │
│                                           │ SwingUtilities.invokeLater (EDT Safe)           │
│                                           ▼                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────────────────┐  │
│  │                         SWING EDT UI SYNCHRONIZER (UISynchronizer)                    │  │
│  │  • `mcreator.reloadWorkspaceTabContents()` • `element.reinit()` • Live Workspace Sync │  │
│  └───────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 9 Yüksek Seviyeli Akıl Aracı (High-Level Public MCP Tools)

| Tool | Parametreler | Açıklama |
| :--- | :--- | :--- |
| **`get_live_context`** | *(Yok)* | MCreator'ın anlık durumunu döner: Kullanıcının o an hangi sekmede çalıştığı, hangi mod elementi editörünün açık olduğu (`ModElementGUI`), seçili öğeler, aktif klasör ve Gradle durumu. |
| **`analyze_project`** | *(Yok)* | Canlı MCreator bağlamını da dahil ederek tüm modun sağlığını, mimarisini, güvenlik risklerini, tick performans açıklarını ve bağımlılıklarını derinlemesine analiz eder. |
| **`inspect_project`** | `targetName`, `queryType` | Semantik graf üzerinden element inceler. `targetName="current"` veya boş bırakılırsa ekranda açık olan aktif editör elementini otomatik odaklar. |
| **`execute_task`** | `intent`, `steps`, `parameters` | Doğal dildeki hedefleri (örn. *"Gece hız veren kılıç yap"*) tek bir atomik transaction ve otomatik rollback garantisiyle yürütür; UI'ı anında tazeler. |
| **`modify_project`** | `modifications` | Çoklu element, özellik, tag veya lokalizasyon değişikliklerini sıralı ve güvenli bir batch halinde uygular. |
| **`create_element`** | `name`, `type`, `properties`, `procedureBinding` | Prosedür bağlama, özellik konfigürasyonu, etiketleme ve lokalizasyonu tek çağrıda birleştiren üst düzey element üreticisi. |
| **`validate_project`** | *(Yok)* | FreeMarker simülasyonu, tick-rate TPS lag dedektörü ve bozuk referans taraması yapar. |
| **`get_project_context`** | *(Yok)* | LLM istemcileri için özel olarak tasarlanmış, token tasarruflu ve canlı UI durumunu da içeren kompakt semantik proje özeti sunar. |
| **`manage_tool_mode`** | `mode` | MCP çalışma modunu anında değiştirir: `DUAL_HYBRID` (Varsayılan: 9 High + 170 Low), `HIGH_LEVEL_ONLY` veya `LEGACY_FULL`. |

---

## 🚀 Quick Start & Installation

### 1. Plugin Kurulumu (Installation)
1. Derlenmiş `MCreatorMCP.zip` dosyasını indirin veya `./gradlew jar` ile derleyin.
2. Dosyayı MCreator eklentiler klasörünüze kopyalayın:
   - **Windows:** `%USERPROFILE%\.mcreator\plugins\MCreatorMCP.zip`
   - **Linux / macOS:** `~/.mcreator\plugins\MCreatorMCP.zip`
3. MCreator'ı başlatın. MCP sunucusu otomatik olarak çalışmaya başlayacaktır (varsayılan port: `5175`).

### 2. LLM İstemcisi Yapılandırması (Connecting LLM Clients)

#### Claude Desktop (`claude_desktop_config.json`)
```json
{
  "mcpServers": {
    "mcreator": {
      "command": "npx",
      "args": ["-y", "mcp-proxy", "http://localhost:5175/mcp"]
    }
  }
}
```

#### Antigravity / Cursor / Roo Code / Continue (Direct HTTP)
- **URL:** `http://localhost:5175/mcp`
- **Protocol:** `Streamable HTTP / SSE`

---

## 📚 170 Araçlık Tam Dahili Yetenek Kataloğu (Full Core Capabilities)

### 🔨 1. Build & Run
| Araç | Açıklama |
| :--- | :--- |
| `buildWorkspace` | Gradle ile projeyi derler (öncesinde otomatik onarım yapar). |
| `regenerateCode` | Tüm kaynak kodlarını sıfır hata garantisiyle yeniden üretir. |
| `runClient` | Minecraft test istemcisini (`runClient`) başlatır. |
| `runServer` | Minecraft test sunucusunu (`runServer`) başlatır. |

### ⚙️ 2. Workspace Yönetimi
| Araç | Açıklama |
| :--- | :--- |
| `getWorkspaceInfo` | Çalışma alanı temel bilgilerini, modid ve element istatistiklerini getirir. |
| `setWorkspaceSettings` | Mod adı, modid, paket adı, yazar ve sürüm ayarlarını günceller. |
| `getGeneratorInfo` | Aktif jeneratör detaylarını ve desteklenen tipleri listeler. |
| `switchGenerator` | Mod yapısını Forge, NeoForge, Fabric veya diğer sürümlere geçirir. |
| `exportWorkspace` | Dağıtıma hazır mod JAR dosyasını dışa aktarır. |

### 🐘 3. Gradle Yönetimi
| Araç | Açıklama |
| :--- | :--- |
| `clearGradleCaches` | Gradle ve MCreator önbellek kilitlerini temizler. |
| `reloadGradleProject` | Gradle bağımlılıklarını ve proje yapısını yeniden senkronize eder. |
| `runGradleTask` | Özel bir Gradle görevini çalıştırır (`task` parametresiyle). |
| `cancelGradleTask` | Çalışmakta olan Gradle görevini iptal eder. |
| `getGradleStatus` | Gradle durumunu (`READY`, `RUNNING`, `ERROR`) ve bayraklarını döndürür. |

### 🧩 4. Mod Elementleri (35+ Tip Desteği)
| Araç | Açıklama |
| :--- | :--- |
| `listModElements` | Çalışma alanındaki mod elementlerini listeler (tip filtresi destekli). |
| `getModElement` | Belirli bir elementin tam JSON tanımını ve meta verilerini getirir. |
| `createElement` | Yeni mod elementi oluşturur (Block, Item, Procedure, Entity, Armor, GUI, Biome vb.). |
| `updateModElement` | Mevcut elementin alanlarını güvenle günceller. |
| `deleteElement` | Mod elementini ve ilişkili kaynak dosyalarını siler. |
| `listModElementTypes` | Desteklenen tüm element tiplerini listeler. |
| `duplicateElement` | Bir mod elementini klonlar. |
| `renameElement` | Mod elementini güvenle yeniden adlandırır. |
| `getElementCode` | Elementin üretilen Java kaynak kodlarını getirir. |
| `listElementEvents` | Elementin olay tetikleyicilerini ve prosedür alanlarını listeler. |
| `searchElements` | Elementler arasında arama yapar. |

### 🧠 5. Prosedür & Blockly Sistemi
| Araç | Açıklama |
| :--- | :--- |
| `createProcedure` | Blockly XML veya hazır eylemlerle prosedür oluşturur. |
| `listProcedureTriggers` | Tüm olay tetikleyicilerini (`player_ticks`, `entity_dies` vb.) bellek önbellekli DTO ve `search`/`group`/`limit` filtreleriyle listeler. |
| `listProcedureBlocks` | Prosedür bloklarını kategori ve kelime filtresiyle arar. |
| `linkProcedureToElement` | Prosedürü herhangi bir elementin olayına tek komutla bağlar. |
| `validateProcedureXML` | Blockly XML dizilimini syntax ve blok bağlantıları açısından doğrular. |
| `listAITasks` | Varlık yapay zeka bloklarını listeler (`BlocklyEditorType.AI_TASK`). |
| `listJSONTriggerBlocks` | Datapack JSON tetikleyici bloklarını listeler (`BlocklyEditorType.JSON_TRIGGER`). |
| `listFeatureBlocks` | Dünya oluşturma özellik bloklarını listeler (`BlocklyEditorType.FEATURE`). |
| `listCommandArgBlocks` | Komut argüman bloklarını listeler (`BlocklyEditorType.COMMAND_ARG`). |

### 🔍 6. Hata Analiz & CodeGen Debugger
| Araç | Açıklama |
| :--- | :--- |
| `analyzeRegenerateErrors` | Kod yenileme öncesi tüm şablon ve null alan risklerini derinlemesine tarar. |
| `testGenerateElement` | Tek bir element için kod üretimini simüle eder (dry-run) ve hataları izole eder. |
| `inspectElementErrors` | Elementin JSON ve bellek bütünlüğünü denetler. |
| `autoFixAllErrors` | Tespit edilen tüm eksik alanları, @Nonnull referansları ve XML'leri otomatik onarır. |

### 📋 7. Canlı Log & Crash Raporu Analizcisi
| Araç | Açıklama |
| :--- | :--- |
| `getGradleConsoleOutput` | MCreator dahili Gradle konsolunun canlı çıktısını filtreli olarak alır. |
| `getMinecraftLogs` | `run/logs/latest.log` ve `debug.log` dosyalarını okur. |
| `analyzeCrashReport` | `run/crash-reports/*.txt` çökme raporunu ayrıştırır; NPE, Ticking Entity veya Mixin hatalarını tespit ederek hatalı elementi işaret eder. |
| `clearConsole` | Gradle konsol çıktısını temizler. |

### 🎨 8. Doku, Model, Ses & Varlık Yönetimi
| Araç | Açıklama |
| :--- | :--- |
| `listTextures` / `addTexture` / `deleteTexture` | Blok, eşya, varlık dokularını yönetir. |
| `inspectTexture` | Doku boyutlarını, formatını ve 2'nin kuvveti kuralını doğrular. |
| `createAnimatedTexture` | Animasyonlu doku (`.png` + `.mcmeta`) üretir. |
| `tintTexture` | Dokulara RGB renk filtresi uygular. |
| `generateTextureTemplate` | Katı renk, ızgara, çerçeve veya gürültü desenli temel doku üretir. |
| `listModels` / `addModel` / `deleteModel` | Java, JSON, OBJ 3D modellerini yönetir. |
| `listSounds` / `addSound` / `deleteSound` | OGG ses kayıtlarını yönetir. |
| `listStructures` / `addStructure` / `deleteStructure` | NBT yapı dosyalarını yönetir. |
| `listAnimations` / `addAnimation` | Bedrock/JSON model animasyonlarını yönetir. |

### 📁 9. Workspace Klasör Ağacı & Düzenleme
| Araç | Açıklama |
| :--- | :--- |
| `getFolderTree` | Çalışma alanındaki klasör hiyerarşisini listeler. |
| `createWorkspaceFolder` | Yeni UI klasörü oluşturur. |
| `deleteWorkspaceFolder` | Klasörü siler ve elementleri güvenle taşır. |
| `moveElementToFolder` | Elementi istenen klasöre taşır. |

### 🔒 10. Kod Kilitleme & Toplu İşlem
| Araç | Açıklama |
| :--- | :--- |
| `lockElementCode` | Elle yazılan kodun ezilmesini önlemek için kod kilidini açar/kapatır (`code lock`). |
| `batchUpdateElements` | Birden fazla elementi tek bir işlemde toplu olarak günceller. |
| `cleanWorkspaceBuild` | `build/` ve `.gradle/` kilitlerini temizler. |

### 💾 11. Yedekleme & Snapshot Sistemi
| Araç | Açıklama |
| :--- | :--- |
| `createWorkspaceBackup` | Çalışma alanının anlık `.zip` yedeğini oluşturur. |
| `listWorkspaceBackups` | Kayıtlı yedekleri listeler (tarih ve boyutlarıyla). |
| `restoreWorkspaceBackup` | Yedekten çalışma alanını geri yükler. |

### 🔌 12. Mod API Yönetimi
| Araç | Açıklama |
| :--- | :--- |
| `listModAPIs` | Desteklenen Mod API'lerini (Curios, JEI, Patchouli, Geckolib vb.) listeler. |
| `setModAPIState` | Mod API'sini tek komutla projede aktif eder veya kaldırır. |

### ⚡ 13. Sistem Performansı & JVM Tanılama
| Araç | Açıklama |
| :--- | :--- |
| `getSystemPerformance` | RAM kullanımı, aktif thread'ler, CPU durumu ve JVM çalışma süresini görüntüler. |
| `runGarbageCollector` | MCreator belleğini temizlemek için JVM Garbage Collection tetikler. |

### 🌐 14. Lokalizasyon, Tag & Referanslar
| Araç | Açıklama |
| :--- | :--- |
| `listLocalizations` / `getLocalization` / `setLocalization` / `deleteLocalization` | Dil dosyalarını yönetir. |
| `listTags` / `addTag` / `deleteTag` | Block, Item, Entity, Biome tag'lerini yönetir. |
| `findReferences` / `findBrokenReferences` | Element referanslarını ve bozuk bağlantıları takip eder. |

### 📜 15. Vanilla Minecraft Kayıt Defterleri
| Araç | Açıklama |
| :--- | :--- |
| `getMinecraftBlocks` / `getMinecraftItems` / `getMinecraftEntities` / `getMinecraftBiomes` | Standart Minecraft verilerini listeler. |
| `getMinecraftSounds` / `getMinecraftEnchantments` / `getMinecraftPotions` | Ses, büyü ve iksir kayıtlarını listeler. |
| `getMinecraftParticles` / `getMinecraftDamageTypes` / `getMinecraftAttributes` | Parçacık, hasar ve nitelik kayıtlarını listeler. |

### 📂 16. Dosya, Sekme & Ayarlar
| Araç | Açıklama |
| :--- | :--- |
| `readFile` / `writeFile` / `listFiles` / `getSourceCode` | Dosya ve kaynak kod işlemlerini yürütür. |
| `getCreativeTabOrder` / `setCreativeTabOrder` | Yaratıcı sekme sıralamasını yönetir. |
| `getPluginInfo` / `getMCreatorVersion` | Eklenti ve sürüm bilgilerini döndürür. |
| `getWorkspaceUserSettings` / `getPreferences` | Proje ve global MCreator ayarlarını okur. |

### 🔧 17. Granüler JSON Alan Düzenleyicisi (Tools 110-114)
| Araç | Açıklama |
| :--- | :--- |
| `patchElementProperty` | Element JSON'undaki belirli bir alanı nokta notasyonuyla (`definition.fuelEnergy`) günceller. |
| `getElementProperty` | Element JSON'undaki belirli bir alanı doğrudan okur. |
| `removeElementProperty` | Element JSON'undan belirli bir özelliği kaldırır. |
| `bulkPatchElements` | Belirli bir element tipindeki tüm öğelerin özelliğini toplu olarak günceller. |
| `compareElements` | İki mod elementinin tanımlarını karşılaştırarak fark raporu (diff) üretir. |

### 🛡️ 18. Derin Statik Kod & Güvenlik Analizi (Tools 115-119)
| Araç | Açıklama |
| :--- | :--- |
| `analyzePerformanceBottlenecks` | Tick tetikleyicilerinde (`player_ticks` vb.) çalışan ağır döngü ve entity aramalarını tespit eder. |
| `analyzeSecurityRisks` | İzin seviyesi 0 olan komutları ve OP yetkili sunucu komutlarını denetler. |
| `analyzeMissingLocalizations` | Tüm dillerdeki eksik çeviri anahtarlarını tespit eder. |
| `analyzeUnusedAssets` | Projede kullanılmayan doku, model ve ses dosyalarını tespit eder. |
| `analyzeCyclicDependencies` | Prosedür ve elementler arasındaki döngüsel bağımlılıkları (`A -> B -> A`) çıkarır. |

### ☕ 19. Java AST & Kaynak Kod Manipülasyonu (Tools 120-125)
| Araç | Açıklama |
| :--- | :--- |
| `insertCodeSnippet` | Java dosyasına belirli bir konuma veya sınıfa güvenle kod enjekte eder. |
| `replaceCodeSnippet` | Java kodunda regex veya düz metin değişimi yapar. |
| `addJavaImport` | Java dosyasına import satırı ekler. |
| `removeJavaImport` | Java dosyasından kullanılmayan import satırını kaldırır. |
| `formatJavaCode` | Java kaynak kodunu girintiler ve parantez yapısıyla biçimlendirir. |
| `listClassMembers` | Java sınıfındaki tüm method ve alanları satır numaralarıyla listeler. |

### 🧩 20. Gelişmiş Blockly XML Düzenleyicisi (Tools 126-131)
| Araç | Açıklama |
| :--- | :--- |
| `findBlocklyNodes` | Prosedür XML'inde belirli tipteki blok düğümlerini arar. |
| `replaceBlocklyField` | Prosedür XML'indeki blok alan değerlerini (`field`) doğrudan günceller. |
| `insertBlocklyStatement` | Prosedür XML'ine yeni blok veya eylem parçası ekler. |
| `removeBlocklyNode` | Prosedür XML'inden bir bloğu güvenle siler. |
| `convertBlocklyToSummary` | Prosedür XML'ini okunabilir mantıksal özet adımlarına dönüştürür. |
| `extractProcedureVariables` | Prosedür XML'indeki yerel değişkenleri çıkarır. |

### 🍲 21. Tarif & Ganimet Tablosu Çakışma Analizcisi (Tools 132-135)
| Araç | Açıklama |
| :--- | :--- |
| `analyzeRecipeConflicts` | Aynı girdi ve çıktıya sahip çakışan tarifleri tespit eder. |
| `analyzeLootTableDrops` | Ganimet tablolarını ve düşme olasılıklarını listeler. |
| `editRecipe` | Tarif parametrelerini (tip, grup, TP, pişirme süresi) günceller. |
| `editLootTable` | Ganimet tablosu havuzlarını ve öğelerini günceller. |

### 🖼️ 22. Gelişmiş Doku Manipülasyonu & Normal Map (Tools 136-143)
| Araç | Açıklama |
| :--- | :--- |
| `extractColorPalette` | Dokudaki baskın renk paletini ve yüzdelerini çıkarır. |
| `swapTextureColors` | Dokudaki belirli bir rengi başka bir renkle piksel bazında değiştirir. |
| `resizeTexture` | Dokuyu pikselleşme bozulmadan yeniden boyutlandırır. |
| `rotateFlipTexture` | Dokuyu 90/180/270 derece döndürür veya yatay/dikey çevirir. |
| `adjustTextureChannels` | Dokunun parlaklık ve kontrastını ayarlar. |
| `generateNormalMap` | Dokudan 3D kabartma Normal Map (`_n.png`) üretir. |
| `compositeTextures` | İki dokuyu katmanlar halinde üst üste birleştirir. |
| `cropTexture` | Belirli koordinatlardan dokuyu kırpar. |

### 🧊 23. 3D Model Düzenleyici & UV Analizi (Tools 144-147)
| Araç | Açıklama |
| :--- | :--- |
| `inspectModelUVs` | 3D modelin küplerini ve UV doku haritalandırmasını inceler. |
| `editModelTextures` | 3D modelin doku harita anahtarlarını günceller. |
| `scaleModel` | 3D modelin koordinatlarını ölçeklendirir. |
| `validateModelSchema` | Modelin Java / Bedrock JSON şemasını doğrular. |

### 🔊 24. Ses Olayları & sounds.json Üreticisi (Tools 148-150)
| Araç | Açıklama |
| :--- | :--- |
| `inspectSoundFile` | OGG ses dosyasının boyut ve bütünlüğünü denetler. |
| `editSoundEvent` | Ses olayının kategori, altyazı ve akış parametrelerini günceller. |
| `generateSoundJSON` | Mod seslerinden otomatik `sounds.json` haritası üretir. |

### 🏷️ 25. Tag Düzenleyici & Doğrulama (Tools 151-153)
| Araç | Açıklama |
| :--- | :--- |
| `editTagEntries` | Tag elementine öğe ekler veya çıkartır. |
| `findTagsForElement` | Bir mod elementinin hangi tag'lerde yer aldığını bulur. |
| `validateTags` | Boş veya geçersiz tag referanslarını denetler. |

### 🌐 26. Lokalizasyon & Değişken Derin Düzenleyici (Tools 154-157)
| Araç | Açıklama |
| :--- | :--- |
| `editWorkspaceVariable` | Workspace değişkeninin tipini, kapsamını ve değerini günceller. |
| `batchSetLocalizations` | Çok dilli çevirileri toplu olarak günceller. |
| `autoFillMissingTranslations` | `en_us` dilindeki anahtarları eksik olan diğer dillere otomatik kopyalar. |
| `searchLocalizationKeys` | Çeviri anahtarları ve değerleri arasında arama yapar. |

### 🌌 27. Genişletilmiş Vanilla Kayıt Defterleri (Tools 158-170)
| Araç | Açıklama |
| :--- | :--- |
| `getMinecraftDimensions` | Minecraft boyutlarını (`overworld`, `nether`, `the_end`) listeler. |
| `getMinecraftStructures` | Minecraft yapılarını (köyler, kaleler, tapınaklar) listeler. |
| `getMinecraftBannerPatterns` | Flama desenlerini listeler. |
| `getMinecraftTrimMaterials` | Zırh süsleme materyallerini listeler. |
| `getMinecraftTrimPatterns` | Zırh süsleme desenlerini listeler. |
| `getMinecraftGameRules` | Vanilla oyun kurallarını (`keepInventory` vb.) listeler. |
| `getMinecraftPaintingVariants` | Tablo varyantlarını listeler. |
| `getMinecraftVillagerProfessions` | Köylü mesleklerini listeler. |
| `getMinecraftWolfVariants` | Kurt varyantlarını listeler. |
| `getMinecraftStats` | İstatistik tiplerini listeler. |
| `getMinecraftRecipeTypes` | Tarif tiplerini (`crafting_shaped`, `smelting` vb.) listeler. |
| `getMinecraftEntityCategories` | Varlık kategorilerini (`undead`, `arthropod` vb.) listeler. |
| `getMinecraftSoundCategories` | Ses kategorilerini (`master`, `music`, `player` vb.) listeler. |

---

## 🛠️ Derleme (Building from Source)

```bash
# Projeyi derleyin
./gradlew clean jar

# Üretilen çıktı paketi: build/libs/MCreatorMCP.zip
```

---

## 📄 Lisans & Yasal Uyarı (License & Legal Notice)

Bu proje **GNU General Public License v3.0 (GPL-3.0)** altında lisanslanmıştır.

* **Orijinal Proje:** [modpotato/MCreatorMCP](https://github.com/modpotato/MCreatorMCP) (MIT Lisansı altında başlatılmıştır).
* **Genişletilmiş Sürüm:** Bu çatal (fork), `cmmdx256` tarafından MCreator'ın (GPL-3.0) iç yapısıyla tam entegre çalışacak biçimde genişletilmiş ve **GPL-3.0** lisansı ile sunulmuştur.
* Detaylar için [LICENSE](LICENSE) dosyasına bakabilirsiniz.

## 🔗 Bağlantılar

- [MCreator Resmi Sitesi](https://mcreator.net/)
- [Model Context Protocol (MCP)](https://modelcontextprotocol.io/)
