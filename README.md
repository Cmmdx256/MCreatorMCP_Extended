# MCreator MCP Integration Plugin (v3.0.0)

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![MCreator](https://img.shields.io/badge/MCreator-2020.1%20--%202026.x%2B-orange.svg)](https://mcreator.net/)
[![Java](https://img.shields.io/badge/Java-17%20%7C%2021%2B-red.svg)](https://adoptium.net/)
[![MCP Tools](https://img.shields.io/badge/MCP%20Tools-170%20Tools-brightgreen.svg)](https://modelcontextprotocol.io/)

> **Author:** `modpotato`  
> **Forked, Expanded & Maintained by:** `cmmdx256`

A comprehensive, high-performance **Model Context Protocol (MCP)** server integration plugin for [MCreator](https://mcreator.net/). It exposes **170 native tools**, granular JSON property patching, deep static code analysis (security risks & tick-lag hazards), Java AST manipulation, Blockly XML DOM queries & edits, advanced procedural image/texture processing (normal maps, palette extractor, channel adjustments), recipe/loot table conflict detectors, Minecraft runtime crash & log debuggers, workspace snapshot backups, and full workspace automation to LLM agents (Claude Desktop, Antigravity, Cursor, Roo Code, Continue, etc.).

---

## 🌟 Key Capabilities

### ⚡ 100% Full-to-Full MCreator Control (170 Native Tools)
Direct programmatic access to every subsystem in MCreator: Mod Elements (35+ types), Procedures & Blockly blocks, Tags, Localizations, 3D Models, Sounds, Textures, Animated Textures, Structures, Animations, Creative Tabs, Workspace Variables, Mod APIs, Gradle Tasks, and Workspace Folder trees.

### 🔬 Granular Property Patching & Field Editor
Read, patch, delete, bulk-update, and diff any nested JSON property in any mod element (`definition.foo.bar`) without having to overwrite or re-serialize entire files (`patchElementProperty`, `getElementProperty`, `removeElementProperty`, `bulkPatchElements`, `compareElements`).

### 🛡️ Deep Static Code, Mod Security & Performance Analyzer
Scans workspace procedures and custom commands to identify severe tick performance hazards (e.g. while/repeat loops and area entity searches running on every tick), elevated command execution risks, missing localizations across all languages, unreferenced/orphaned assets, and circular element dependencies.

### 📝 Java Source Code AST & Live Code Editor
Inject code snippets at specific anchors or method boundaries, perform regex block replacements, manage class imports, format source files, and inspect Java class members (fields, methods, constructors) directly.

### 🧩 Blockly XML DOM Query & Node Editor
Search opcode block types, replace field values, insert statement chains, remove nodes while preserving connections, convert procedure logic to readable pseudo-code summaries, and extract procedure-scoped variables.

### 🎨 Advanced Texture Manipulation & Image Processing
Extract dominant color palettes, swap exact hex colors, resize textures with nearest-neighbor interpolation, rotate/flip (90°, 180°, 270°, horizontal/vertical flip), adjust brightness/contrast, generate Sobel normal maps (`_n.png`), composite multiple texture layers, and crop subregions.

### 📋 Minecraft Client / Server Log & Crash Report Debugger
Real-time capture of Gradle console output (`getGradleConsoleOutput`) and Minecraft runtime logs (`latest.log`, `debug.log`). Automatically parses `run/crash-reports/*.txt` to pinpoint the root cause (NullPointerException, Ticking BlockEntity, Missing Registry, Mixin conflicts) and identifies the exact culprit mod element or Java class (`analyzeCrashReport`).

### 📦 Workspace Snapshots & Backup System
Create and restore full `.zip` workspace backups with timestamps (`createWorkspaceBackup`, `listWorkspaceBackups`, `restoreWorkspaceBackup`).

### 🌐 Multi-Transport Architecture
- **HTTP POST**: `http://localhost:<port>/mcp` (Standard JSON-RPC 2.0)
- **SSE Stream**: `http://localhost:<port>/mcp/sse` (Server-Sent Events)
- **Stdio**: Direct standard input/output streaming
- **Health Check**: `http://localhost:<port>/health`
- **Dynamic Port Selection**: Automatically searches from port `5175` onwards to prevent port conflicts.

---

## 🚀 Quick Start & Installation

### 1. Plugin Kurulumu (Installation)
1. Derlenmiş `MCreatorMCP.zip` dosyasını indirin veya projeyi derleyin (`./gradlew jar`).
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

#### Antigravity / Cursor / Roo Code (HTTP Endpoint)
- **URL:** `http://localhost:5175/mcp`
- **Protocol:** `Streamable HTTP / SSE`

---

## 📚 170 Araçlık Tam Referans Kataloğu (Full 170 Tools Reference)

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
| `listProcedureTriggers` | Tüm olay tetikleyicilerini (`player_ticks`, `entity_dies` vb.) hızlı DTO yapısıyla listeler. |
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

# Çıktı dosyası: build/libs/MCreatorMCP.zip
```

---

## 📄 Lisans

Bu proje **GNU General Public License v3.0 (GPL-3.0)** altında lisanslanmıştır. Detaylar için [LICENSE](LICENSE) dosyasına bakabilirsiniz.

## 🔗 Bağlantılar

- [MCreator Resmi Sitesi](https://mcreator.net/)
- [Model Context Protocol (MCP)](https://modelcontextprotocol.io/)