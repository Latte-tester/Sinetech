# 🚀 Latte - Sinetech Cloudstream Eklentisi Deposu

![Latte Repository Banner](img/banner.png)

📦 **Kullanıma Hazır Eklentiler**
| Eklenti | Versiyon | Lisans |
|---------|----------|--------|
| [PowerDizi](powerDizi) | ![](https://img.shields.io/badge/version-3-blue) | [MIT](LICENSE) |
| [PowerSinema](powerSinema) | ![](https://img.shields.io/badge/version-3-blue) | [MIT](LICENSE) |
| [TvBahcesi](TvBahcesi) | ![](https://img.shields.io/badge/version-1-orange) | [MIT](LICENSE) |
| [DaddyLive](DaddyLive) | ![](https://img.shields.io/badge/version-1-orange) | [MIT](LICENSE) |
| [OhaTO](OhaTO) | ![](https://img.shields.io/badge/version-1-orange) | [MIT](LICENSE) |
| [VavooTO](VavooTO) | ![](https://img.shields.io/badge/version-1-orange) | [MIT](LICENSE) |
| [HuhuTO](HuhuTO) | ![](https://img.shields.io/badge/version-1-orange) | [MIT](LICENSE) |
| [KoolTO](KoolTO) | ![](https://img.shields.io/badge/version-1-orange) | [MIT](LICENSE) |

---

## 🌟 Öne Çıkan Özellikler

✅ TMDB Entegrasyonu ile zengin içerik bilgisi  
🔍 Gelismis arama ve filtreleme  
📥 Çevrimdışı izleme için indirme desteği  
🎨 Kullanıcı dostu modern arayüz  
📺 Canlı TV ve spor yayınları  

[![TMDB API Status](https://img.shields.io/badge/TMDB%20API-Çalışıyor-brightgreen)](https://www.themoviedb.org/)

---

## 📺 PowerDizi

![PowerDizi Arayüz](img/powerdizi/powerboarddiziss.png)

### 🛠 Temel Özellikler
| Kategori | Detaylar |
|----------|----------|
| **Desteklenen Tür** | TV Dizileri |
| **Arama** | Tür/Yıl/Puan filtreleme |
| **Entegrasyon** | TMDB API v3 |
| **Platform** | Android/Windows/Linux/macOS |

```markdown
🔸 Ana Sayfa Özellikleri:
- Popüler diziler
- Yeni eklenenler
- Özel koleksiyonlar
- Kişiselleştirilmiş öneriler
```

---

## 🎬 PowerSinema

![PowerSinema Arayüz](img/powersinema/powerboardsinemass.png)

### 🎞 TMDB Entegrasyon Detayları
| Bilgi | Açıklama |
|-------|-----------|
| Slogan | `movie.tagline` |
| Yönetmen | `credits.crew[0].name` |
| Süre | `runtime` minute |
| Çıkış Tarihi | `release_date` |

---

## 📡 TvBahcesi

![TvBahcesi Arayüz](img/tvbahcesi/tvbahcesi-ss.png)

### 🌍 Desteklenen Kanallar
- 📻 4000+ Uluslararası TV Kanalı
- 🌐 155+ Ülkeye özel içerik
- 🎭 Kanal alternatifleri parantez içinde rakamsal değerle gösterilmiştir.

### 📺 Özellikler
| Kategori | Detaylar |
|----------|----------|
| **Desteklenen Tür** | Canlı TV |
| **Arama** | Kanal adı ile hızlı arama |
| **Gruplar** | Ülke ve kategori bazlı gruplandırma |
| **Öneriler** | Aynı kategorideki benzer kanallar |

---

## 🏆 DaddyLive Mor Spor ve Events

### 🎮 Spor Yayınları ve Etkinlikler
| Kategori | Detaylar |
|----------|----------|
| **Desteklenen Tür** | Canlı TV ve Spor Yayınları |
| **İçerik** | Spor müsabakaları ve özel etkinlikler |
| **Gruplar** | Spor türlerine göre kategorize edilmiş |
| **Arama** | Etkinlik ve kanal adı ile hızlı arama |

### 🌟 Özellikler
- 🏀 Çeşitli spor dallarında canlı yayınlar
- 🌍 Uluslararası spor etkinlikleri
- 🏆 Turnuvalar ve özel organizasyonlar
- 📊 Grup bazlı düzenlenmiş içerik

---

## 📺 TO Serisi Eklentileri (OhaTO, VavooTO, HuhuTO, KoolTO)

### 🇹🇷 Türkiye Televizyon Kanalları
| Kategori | Detaylar |
|----------|----------|
| **Desteklenen Tür** | Canlı TV |
| **İçerik** | Türkiye'ye ait ulusal ve spor kanalları |
| **Gruplar** | Kategori bazlı düzenlenmiş |
| **Arama** | Kanal adı ile hızlı arama |

### 🌟 Ortak Özellikler
- 📡 Türkiye'nin popüler ulusal kanalları
- 🏆 Spor kanalları ve özel yayınlar
- 🔄 Alternatif kaynaklar ile kesintisiz izleme
- 🌐 VPN ile farklı ülke seçenekleri

### 🔍 Eklentiler Arası Farklar
```markdown
Bu dört eklenti (OhaTO, VavooTO, HuhuTO, KoolTO) birbirinin aynısı yapıda olmasına rağmen:
- Farklı referans ve user agent'lar barındırırlar
- VPN kullanımında ülke seçimlerine göre farklı performans gösterebilirler
- Erişim engeli durumunda alternatif olarak kullanılabilirler
```

---

## 🛠 Ortak Yapılandırma

### 🔑 TMDB API Kurulumu
1. [TMDB](https://www.themoviedb.org/) üzerinden API anahtarı alın
2. `Ayarlar > API Yapılandırması` bölümüne girin
3. Değişiklikleri kaydedin

```groovy
// build.gradle içinde gerekli bağımlılık
dependencies {
    implementation 'com.sinetech:tmdb-integration:2.4.1'
}
```

---

## 🤝 Katkıda Bulunanlar

| Geliştirici | Rol |
|-------------|-----|
| [GitLatte](https://github.com/GitLatte) | Backend Geliştirme |
| [patr0nq](https://github.com/patr0nq) | Güncelleme ve Geliştirme Ortağı|
| [keyiflerolsun](https://github.com/keyiflerolsun) | Eklenti kodları ilham kaynağı |
| [doGior](https://github.com/DoGior) | Eklenti kodları ilham kaynağı |
| [powerboard](https://forum.sinetech.tr/uye/powerboard.3822/) | PowerDizi-PowerSinema liste sahibi |
| [tıngırmıngır](https://forum.sinetech.tr/uye/tingirmingir.137/) | TMDB ve Tv Bahçesi ilham kaynağı |
| [mooncrown](https://forum.sinetech.tr/uye/mooncrown.10472/) | Sinema/Dizi eklentisi "İzlemeye Devam Et" başlatma sebebi |
| [nedirne](https://forum.sinetech.tr/uye/nedirne.13409/) | Sinema/Dizi eklentisi TMDB olayını başlatma sebebi |

📬 **Destek İletişim:** [Latte](https://forum.sinetech.tr/konu/powerboard-film-ve-dizi-arsivine-ozel-cloudstream-deposu.3672/)

---

🔔 **Not:** Repoyu eklemek için Cloudstream içerisindeki Depo Ekle alanında Depo URL kısmına **"Latte"** yazmanız yeterlidir.
