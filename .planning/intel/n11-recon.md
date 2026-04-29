# n11.com Recon Report

**Captured:** 2026-04-29
**Source:** https://www.n11.com — public-page surface (no login, no real cart)
**Tool:** Playwright @playwright/test 1.59.x
**Recon project:** `tools/recon/`
**Consumer:** Phase 10 (Storefront), Phase 11 (Chat Bubble)

## 1. Page Inventory

| Page | URL Captured | Screenshot | Element Detail Shots | Capture Date |
|------|--------------|------------|----------------------|--------------|
| Homepage | https://www.n11.com/ | screenshots/homepage-fullpage.png | screenshots/homepage-header-element.png | 2026-04-29 |
| Category (Elektronik) | https://www.n11.com/elektronik | screenshots/category-elektronik-fullpage.png | — | 2026-04-29 |
| Product Detail (PDP) | https://www.n11.com/urun/apple-macbook-air-mc6t4tua-16-gb-256-gb-ssd-136-macos-dizustu-bilgisayar-74956976?magaza=troyapple | screenshots/pdp-fullpage.png | screenshots/pdp-cta-element.png | 2026-04-29 |
| Cart (empty) | https://www.n11.com/sepetim | screenshots/cart-fullpage.png | — | 2026-04-29 |
| Checkout step 1 | https://www.n11.com/genel/odeme-secenekleri-393251 | screenshots/checkout-step1-fullpage.png | — | 2026-04-29 |
| Account (anonymous landing) | https://www.n11.com/giris-yap?redirectUrl=/hesabim | screenshots/account-fullpage.png | — | 2026-04-29 |
| Login | https://www.n11.com/giris-yap | screenshots/login-fullpage.png | — | 2026-04-29 |

## 2. Turkish Copy Catalog

> ≥30 rows. Used verbatim by Phase 10 for FE-13 (Turkish UI copy) and Phase 8 for the chat assistant's grounded vocabulary (Pitfall #20 prevention).

| # | Phrase (TR) | English Gloss | Source Page | Section / Component |
|---|-------------|---------------|-------------|---------------------|
| 1 | Giriş Yap | — | account | — |
| 2 | Üye Ol
             250 TL İndirim Kazan | — | account | — |
| 3 | 250 TL İndirim Kazan | — | account | — |
| 4 | Diğer Seçenekler | — | account | — |
| 5 | Yardıma mı ihtiyacın var? | — | account | — |
| 6 | Gizlilik ve Kişisel Verilerin Korunması Politikası | — | account | — |
| 7 | Kullanım Koşullarımız | — | account | — |
| 8 | Çerez Ayarları | — | account | — |
| 9 | Evet | — | account | — |
| 10 | Daha Sonra | — | account | — |
| 11 | Adres Ekle | — | cart | — |
| 12 | HESABIM | — | cart | — |
| 13 | Üye Ol | — | cart | — |
| 14 | Giriş Yap / Üye Ol | — | cart | — |
| 15 | Ana Sayfa | — | cart | — |
| 16 | Kategoriler | — | cart | — |
| 17 | Moda | — | cart | — |
| 18 | Moda Ana Sayfa | — | cart | — |
| 19 | Elektronik | — | cart | — |
| 20 | Elektronik Ana Sayfa | — | cart | — |
| 21 | Bilgisayar | — | cart | — |
| 22 | Elektrikli Ev Aletleri | — | cart | — |
| 23 | Beyaz Eşya | — | cart | — |
| 24 | Mobilya | — | cart | — |
| 25 | Ev Tekstili | — | cart | — |
| 26 | Mutfak Gereçleri | — | cart | — |
| 27 | Evcil Hayvan Ürünleri | — | cart | — |
| 28 | Süpermarket | — | cart | — |
| 29 | Bebek Giyim | — | cart | — |
| 30 | Hamile Giyim | — | cart | — |
| 31 | Bebek Arabaları | — | cart | — |
| 32 | Biberon ve Aksesuarları | — | cart | — |
| 33 | Emzirme Ürünleri | — | cart | — |
| 34 | Bebek Güvenlik | — | cart | — |
| 35 | Bebek Oyuncakları | — | cart | — |
| 36 | Cilt Bakımı | — | cart | — |
| 37 | Makyaj | — | cart | — |
| 38 | Kadın Bakım Ürünleri | — | cart | — |
| 39 | Erkek Bakım Ürünleri | — | cart | — |
| 40 | Cinsel Ürünler | — | cart | — |
| 41 | Saat | — | cart | — |
| 42 | Güneş Gözlüğü | — | cart | — |
| 43 | Altın Takılar | — | cart | — |
| 44 | Pırlanta Takılar | — | cart | — |
| 45 | Gümüş Takılar | — | cart | — |
| 46 | Çelik Takılar | — | cart | — |
| 47 | Bijuteri Takılar | — | cart | — |
| 48 | Aksesuar | — | cart | — |
| 49 | Takı Aksesuarları | — | cart | — |
| 50 | Kış Sporları | — | cart | — |
| 51 | Su Sporları | — | cart | — |
| 52 | Kitap, Müzik, Film, Oyun | — | cart | — |
| 53 | Kitap, Müzik, Film, Oyun Ana Sayfa | — | cart | — |
| 54 | Kitap | — | cart | — |
| 55 | Film | — | cart | — |
| 56 | Müzik | — | cart | — |
| 57 | Düğün, Davet, Organizasyon | — | cart | — |
| 58 | El İşi Ürünleri | — | cart | — |
| 59 | Yaşam ve Etkinlik | — | cart | — |
| 60 | Yedek Parça | — | cart | — |
| 61 | Motosiklet | — | cart | — |
| 62 | Traktör | — | cart | — |
| 63 | Mağaza Mesajlarım | — | cart | — |
| 64 | POPÜLER SAYFALAR | — | cart | — |
| 65 | pet11 | — | cart | — |
| 66 | Kaçmaz Teklifler | — | cart | — |
| 67 | Çok Satanlar | — | cart | — |
| 68 | Hediye Rehberi | — | cart | — |
| 69 | DİĞER | — | cart | — |
| 70 | n11 Asistan | — | cart | — |
| 71 | Hakkımızda | — | cart | — |
| 72 | Gizlilik Politikası | — | cart | — |
| 73 | Sepetin Boş Görünüyor | — | cart | — |
| 74 | Kuponlarla indirimli alışveriş yap! | — | cart | — |
| 75 | Favori ürünlerini ekle ve takip et! | — | cart | — |
| 76 | Hemen Giriş Yap | — | cart | — |
| 77 | Son Baktıklarım | — | cart | — |
| 78 | Favorilerim | — | cart | — |
| 79 | Sana Özel | — | cart | — |
| 80 | Her Alışverişte Kupon Fırsatları | — | cart | — |
| 81 | Her gün Yeni Ürünler ve Fırsatlar | — | cart | — |
| 82 | Herkese Uygun Ödeme Yöntemleri | — | cart | — |
| 83 | Kolay İade ve İptal | — | cart | — |
| 84 | Mağazalar | — | cart | — |
| 85 | Mağaza Girişi | — | cart | — |
| 86 | Ücretsiz Mağaza Aç | — | cart | — |
| 87 | Yeni Mağaza Rehberi | — | cart | — |
| 88 | Mağaza Puanı Hesaplaması | — | cart | — |
| 89 | Mağaza Yardım Merkezi | — | cart | — |
| 90 | Kargo Rehberi | — | cart | — |
| 91 | n11.com | — | cart | — |
| 92 | Marka Koruma Merkezi | — | cart | — |
| 93 | Markalar | — | cart | — |
| 94 | İletişim | — | cart | — |
| 95 | Müşteriler | — | cart | — |
| 96 | Yeni Üye Rehberi | — | cart | — |
| 97 | Yardım | — | cart | — |
| 98 | Ödeme Seçenekleri | — | cart | — |
| 99 | İşlem Rehberi | — | cart | — |
| 100 | Kullanıcı Güvenliği | — | cart | — |
| 101 | Ürün Güvenliği | — | cart | — |
| 102 | Site Haritası | — | cart | — |
| 103 | Kupon Kullanım Rehberi | — | cart | — |
| 104 | Ürün Rozetleri Rehberi | — | cart | — |
| 105 | n11 Influencer Programı | — | cart | — |
| 106 | Altın | — | cart | — |
| 107 | Robot Süpürge | — | cart | — |
| 108 | iPhone 14 | — | cart | — |
| 109 | Akıllı Saat | — | cart | — |
| 110 | Airpods Kulaklık | — | cart | — |
| 111 | Playstation 5 | — | cart | — |
| 112 | Apple Watch | — | cart | — |
| 113 | Bluetooth Kulaklık | — | cart | — |
| 114 | Kız Çocuk Bot | — | cart | — |
| 115 | Erkek Sweatshirt | — | cart | — |
| 116 | Cep Telefonu | — | cart | — |
| 117 | Öne Çıkan Sayfalar | — | cart | — |
| 118 | iPhone 15 | — | cart | — |
| 119 | Kadın Mont | — | cart | — |
| 120 | Derin Dondurucu | — | cart | — |
| 121 | iPhone 16 | — | cart | — |
| 122 | Laptop | — | cart | — |
| 123 | Apple Watch 9 | — | cart | — |
| 124 | Kadın Bot | — | cart | — |
| 125 | Dyson Hava Temizleyici | — | cart | — |
| 126 | Kahve Makinesi | — | cart | — |
| 127 | Bizi Takip Edin | — | cart | — |
| 128 | Bloga Göz At | — | cart | — |
| 129 | Öne Çıkan 
                                    Dizüstü Bilgisayarlar
                                 
                                    Şimdi Keşfet | — | category-elektronik | — |
| 130 | Öne Çıkan | — | category-elektronik | — |
| 131 | Şimdi Keşfet | — | category-elektronik | — |
| 132 | Seçili Elektronik Ürünlerde | — | category-elektronik | — |
| 133 | Alışverişe Başla | — | category-elektronik | — |
| 134 | Grundig'in  Hayatı Kolaylaştıran | — | category-elektronik | — |
| 135 | Hayal Ettiğin Akıllı  Temizlik Ürünlerinde | — | category-elektronik | — |
| 136 | Seçili Ürünlerde  12.999 TL'ye | — | category-elektronik | — |
| 137 | Ürünlere Git | — | category-elektronik | — |
| 138 | Seçili Beyaz Eşyalarda | — | category-elektronik | — |
| 139 | MediaMarkt Mağazasının | — | category-elektronik | — |
| 140 | Mutfağında ve Evinde Fark Yaratacak | — | category-elektronik | — |
| 141 | Sharge Türkiye Mağazasının | — | category-elektronik | — |
| 142 | Şimdi Başla | — | category-elektronik | — |
| 143 | Taşınabilir Vantilatörlerde | — | category-elektronik | — |
| 144 | Şimdi Yakala | — | category-elektronik | — |
| 145 | HB Bilişim Yenilenmiş Cep Telefonlarında | — | category-elektronik | — |
| 146 | Sakın Kaçırma | — | category-elektronik | — |
| 147 | 1.000 TL Altı | — | category-elektronik | — |
| 148 | BUFFLABS Mağazasına Özel | — | category-elektronik | — |
| 149 | Öne Çıkan Ürünler | — | category-elektronik | — |
| 150 | Ücretsiz Kargo | — | category-elektronik | — |
| 151 | 45.999 TL | — | category-elektronik | — |
| 152 | 21.999 TL | — | category-elektronik | — |
| 153 | SEPETTE | — | category-elektronik | — |
| 154 | 20.187,02 TL | — | category-elektronik | — |
| 155 | 10.599 TL | — | category-elektronik | — |
| 156 | 999 TL | — | category-elektronik | — |
| 157 | 2.049 TL | — | category-elektronik | — |
| 158 | 1.689 TL | — | category-elektronik | — |
| 159 | 46.899 TL | — | category-elektronik | — |
| 160 | 44.131,96 TL | — | category-elektronik | — |
| 161 | 149,40 TL | — | category-elektronik | — |
| 162 | 149 TL | — | category-elektronik | — |
| 163 | Dijital Kod | — | category-elektronik | — |
| 164 | Popüler Ürünler | — | category-elektronik | — |
| 165 | Cep Sigorta | — | category-elektronik | — |
| 166 | Sigorta | — | category-elektronik | — |
| 167 | 16.201,39 TL | — | category-elektronik | — |
| 168 | 15.877,36 TL | — | category-elektronik | — |
| 169 | 32.330 TL | — | category-elektronik | — |
| 170 | 28.773,70 TL | — | category-elektronik | — |
| 171 | 968,75 TL | — | category-elektronik | — |
| 172 | 12.999 TL | — | category-elektronik | — |
| 173 | 10.972,50 TL | — | category-elektronik | — |
| 174 | 9.655,80 TL | — | category-elektronik | — |
| 175 | Blog11 | — | category-elektronik | — |
| 176 | Detaylı Bilgi | — | category-elektronik | — |
| 177 | En Çok Aranan Kelimeler | — | category-elektronik | — |
| 178 | Tablet | — | category-elektronik | — |
| 179 | Ankastre Set | — | category-elektronik | — |
| 180 | Apple | — | category-elektronik | — |
| 181 | Arduino Uno | — | category-elektronik | — |
| 182 | Asus Laptop | — | category-elektronik | — |
| 183 | Beko Kurutma Makinesi | — | category-elektronik | — |
| 184 | Beyaz Eşya Seti | — | category-elektronik | — |
| 185 | Bulaşık Makinesi | — | category-elektronik | — |
| 186 | Buzdolabı | — | category-elektronik | — |
| 187 | Dikey Süpürge | — | category-elektronik | — |
| 188 | Dikiş Makinesi | — | category-elektronik | — |
| 189 | Dondurma Makinesi | — | category-elektronik | — |
| 190 | Dyson Süpürge | — | category-elektronik | — |
| 191 | Elektrikli Süpürge | — | category-elektronik | — |
| 192 | Filtre Kahve Makinesi | — | category-elektronik | — |
| 193 | Halı Yıkama Makinesi | — | category-elektronik | — |
| 194 | JBL Kulaklık | — | category-elektronik | — |
| 195 | Klima | — | category-elektronik | — |
| 196 | Kurutma Makinesi | — | category-elektronik | — |
| 197 | Lenovo Laptop | — | category-elektronik | — |
| 198 | Mac Mini | — | category-elektronik | — |
| 199 | Meyve Sıkacağı | — | category-elektronik | — |
| 200 | Monster Notebook | — | category-elektronik | — |
| 201 | Oyun Bilgisayarı | — | category-elektronik | — |
| 202 | Philips Kahve Makinesi | — | category-elektronik | — |
| 203 | Philips Süpürge | — | category-elektronik | — |
| 204 | Pirantech Buharlı Temizleyici | — | category-elektronik | — |
| 205 | Powerbank | — | category-elektronik | — |
| 206 | Samsung Telefonlar | — | category-elektronik | — |
| 207 | Samsung s24 Ultra | — | category-elektronik | — |
| 208 | Su Arıtma Cihazı | — | category-elektronik | — |
| 209 | Su Sebili | — | category-elektronik | — |
| 210 | Televizyon | — | category-elektronik | — |
| 211 | Thomson Projeksiyon | — | category-elektronik | — |
| 212 | Tost Makinesi | — | category-elektronik | — |
| 213 | Vantilatör | — | category-elektronik | — |
| 214 | Waffle Makinesi | — | category-elektronik | — |
| 215 | iphone 14 pro | — | category-elektronik | — |
| 216 | Çamaşır Makinesi | — | category-elektronik | — |
| 217 | Ütü | — | category-elektronik | — |
| 218 | İphone 14 | — | category-elektronik | — |
| 219 | Devamını Göster | — | category-elektronik | — |
| 220 | Son Model Televizyon Modelleri | — | category-elektronik | — |
| 221 | TV ünitesi | — | category-elektronik | — |
| 222 | Masaüstü Bilgisayar | — | category-elektronik | — |
| 223 | Son Model Akıllı Telefonlar | — | category-elektronik | — |
| 224 | fotoğraf makinesi | — | category-elektronik | — |
| 225 | Her Evin İhtiyacı Beyaz Eşyalar | — | category-elektronik | — |
| 226 | Sinema keyfi | — | category-elektronik | — |
| 227 | Kuponlarım | — | homepage | — |
| 228 | Kampanya Bilgisi | — | homepage | — |
| 229 | Ürünleri Keşfet | — | homepage | — |
| 230 | Diva Ürünlerinde  
                                    Kaçmaz Fırsatlar 
                                 
                                    Alışverişe Başla | — | homepage | — |
| 231 | Diva Ürünlerinde | — | homepage | — |
| 232 | Tümünü Gör | — | homepage | — |
| 233 | 03 | — | homepage | — |
| 234 | 47 | — | homepage | — |
| 235 | 37 | — | homepage | — |
| 236 | 2. Ürüne 65 TL İndirim | — | homepage | — |
| 237 | 287,78 TL | — | homepage | — |
| 238 | 259 TL | — | homepage | — |
| 239 | Puffy Semmy Rollpack Yastık 2'li Beyaz | — | homepage | — |
| 240 | 10 günün en düşük fiyatı! | — | homepage | — |
| 241 | 369,91 TL | — | homepage | — |
| 242 | 319 TL | — | homepage | — |
| 243 | 1,500 TL ye 50 TL indirim | — | homepage | — |
| 244 | 1.199 TL | — | homepage | — |
| 245 | 1.189,41 TL | — | homepage | — |
| 246 | Romanson RM0B14LLGGMS1G Kadın Kol Saati | — | homepage | — |
| 247 | 1.395,46 TL | — | homepage | — |
| 248 | 1.394,07 TL | — | homepage | — |
| 249 | 12,999 TL ye %10 indirim | — | homepage | — |
| 250 | 799 TL | — | homepage | — |
| 251 | 449 TL | — | homepage | — |
| 252 | 308 TL | — | homepage | — |
| 253 | 305,90 TL | — | homepage | — |
| 254 | 249,99 TL | — | homepage | — |
| 255 | 244,99 TL | — | homepage | — |
| 256 | 1.079,99 TL | — | homepage | — |
| 257 | 749 TL | — | homepage | — |
| 258 | 179 TL | — | homepage | — |
| 259 | 153,94 TL | — | homepage | — |
| 260 | 1,000 TL ye 300 TL indirim | — | homepage | — |
| 261 | 699 TL | — | homepage | — |
| 262 | Avantajlı Ürünler Tümünü Gör | — | homepage | — |
| 263 | Avantajlı Ürünler | — | homepage | — |
| 264 | 3,500 TL ye 500 TL indirim | — | homepage | — |
| 265 | 177,91 TL | — | homepage | — |
| 266 | 169,90 TL | — | homepage | — |
| 267 | 999,99 TL | — | homepage | — |
| 268 | 170 TL | — | homepage | — |
| 269 | 2. Ürüne %20 İndirim | — | homepage | — |
| 270 | 479,40 TL | — | homepage | — |
| 271 | 399,50 TL | — | homepage | — |
| 272 | 329,90 TL | — | homepage | — |
| 273 | 263,92 TL | — | homepage | — |
| 274 | 629,80 TL | — | homepage | — |
| 275 | 606,30 TL | — | homepage | — |
| 276 | 1,250 TL ye %10 indirim | — | homepage | — |
| 277 | 490 TL | — | homepage | — |
| 278 | 199 TL | — | homepage | — |
| 279 | 129,51 TL | — | homepage | — |
| 280 | 128,07 TL | — | homepage | — |
| 281 | 849,99 TL | — | homepage | — |
| 282 | 679,99 TL | — | homepage | — |
| 283 | 667 TL | — | homepage | — |
| 284 | 466,90 TL | — | homepage | — |
| 285 | 615,12 TL | — | homepage | — |
| 286 | 830,01 TL | — | homepage | — |
| 287 | 747,01 TL | — | homepage | — |
| 288 | 195 TL | — | homepage | — |
| 289 | Vitrin Kampanyaları | — | homepage | — |
| 290 | %30 Sepette İndirim | — | homepage | — |
| 291 | Sepette %10 İndirim | — | homepage | — |
| 292 | %10 Sepette İndirim | — | homepage | — |
| 293 | %20 Sepette İndirim | — | homepage | — |
| 294 | Seçili Islak Mendillerde %20 Sepette İndirim! | — | homepage | — |
| 295 | Seçili Bebek Bezlerinde %20 Sepette İndirim! | — | homepage | — |
| 296 | Seçili Deterjan Ürünlerinde %20 Sepette İndirim1 | — | homepage | — |
| 297 | Seçili Kağıt Ürünlerinde %20 Sepette İndirim! | — | homepage | — |
| 298 | Sepette %18 İndirim | — | homepage | — |
| 299 | Sepette %25 İndirim | — | homepage | — |
| 300 | Sepette %15 İndirim | — | homepage | — |
| 301 | Sepette %20 İndirim | — | homepage | — |
| 302 | Sepette %20 indirim | — | homepage | — |
| 303 | Sepette 500 TL İndirim | — | homepage | — |
| 304 | Katlanan Kuponlar için hemen giriş yap! | — | homepage | — |
| 305 | Detaya Git | — | homepage | — |
| 306 | 1.Kupon | — | homepage | — |
| 307 | 2.Kupon | — | homepage | — |
| 308 | 3.Kupon | — | homepage | — |
| 309 | 4.Kupon | — | homepage | — |
| 310 | 5.Kupon | — | homepage | — |
| 311 | 2.099,95 TL | — | homepage | — |
| 312 | 705,58 TL | — | homepage | — |
| 313 | 1.149 TL | — | homepage | — |
| 314 | 942,18 TL | — | homepage | — |
| 315 | 2.214,99 TL | — | homepage | — |
| 316 | 1.993,49 TL | — | homepage | — |
| 317 | 949 TL | — | homepage | — |
| 318 | 854,10 TL | — | homepage | — |
| 319 | 40.000 TL | — | homepage | — |
| 320 | 38.800 TL | — | homepage | — |
| 321 | 2. Ürüne %15 İndirim | — | homepage | — |
| 322 | 319,90 TL | — | homepage | — |
| 323 | 289 TL | — | homepage | — |
| 324 | 299,99 TL | — | homepage | — |
| 325 | 23.511,51 TL | — | homepage | — |
| 326 | 977,50 TL | — | homepage | — |
| 327 | 954,50 TL | — | homepage | — |
| 328 | 2.569 TL | — | homepage | — |
| 329 | 2.543 TL | — | homepage | — |
| 330 | 3.515,40 TL | — | homepage | — |
| 331 | Guess Gugw0468l1 Kadın Kol Saati | — | homepage | — |
| 332 | 4.160,11 TL | — | homepage | — |
| 333 | 3.619,30 TL | — | homepage | — |
| 334 | 73.999 TL | — | homepage | — |
| 335 | 72.999 TL | — | homepage | — |
| 336 | 28.000 TL | — | homepage | — |
| 337 | 25.799 TL | — | homepage | — |
| 338 | 27.499 TL | — | homepage | — |
| 339 | 27.399 TL | — | homepage | — |
| 340 | 39.999 TL | — | homepage | — |
| 341 | 19.999 TL | — | homepage | — |
| 342 | 16.079,16 TL | — | homepage | — |
| 343 | 30.499 TL | — | homepage | — |
| 344 | 29.999 TL | — | homepage | — |
| 345 | 31.999 TL | — | homepage | — |
| 346 | 29.899 TL | — | homepage | — |
| 347 | 10.999 TL | — | homepage | — |
| 348 | 10.699 TL | — | homepage | — |
| 349 | 31.359,02 TL | — | homepage | — |
| 350 | 19.499 TL | — | homepage | — |
| 351 | 18.989 TL | — | homepage | — |
| 352 | Diva Ürünler | — | homepage | — |
| 353 | Linki Kopyala | — | homepage | — |
| 354 | 672,49 TL | — | homepage | — |
| 355 | 665,10 TL | — | homepage | — |
| 356 | 680,18 TL | — | homepage | — |
| 357 | 806,65 TL | — | homepage | — |
| 358 | Karaca Aksel Bamboo Ekmek Kutusu | — | homepage | — |
| 359 | 699,98 TL | — | homepage | — |
| 360 | Kutulu Piyanolu Mantar Oyun Halısı | — | homepage | — |
| 361 | 679 TL | — | homepage | — |
| 362 | 1,250 TL ye 100 TL indirim | — | homepage | — |
| 363 | 700 TL | — | homepage | — |
| 364 | 595 TL | — | homepage | — |
| 365 | 699,99 TL | — | homepage | — |
| 366 | 622,99 TL | — | homepage | — |
| 367 | Aprilla Ahs 2026b Saç Düzleştirici Siyah | — | homepage | — |
| 368 | 590,59 TL | — | homepage | — |
| 369 | 584,10 TL | — | homepage | — |
| 370 | 249,90 TL | — | homepage | — |
| 371 | UçUç'la Harcamalarının %3'ünü Geri Kazan | — | homepage | — |
| 372 | 1.499,99 TL | — | homepage | — |
| 373 | 1,200 TL ye 200 TL indirim | — | homepage | — |
| 374 | 599,45 TL | — | homepage | — |
| 375 | Guess Guu1423l3m Kadın Kol Saati | — | homepage | — |
| 376 | 4.810,35 TL | — | homepage | — |
| 377 | 4.185 TL | — | homepage | — |
| 378 | 6.499 TL | — | homepage | — |
| 379 | 5.699 TL | — | homepage | — |
| 380 | English Home Calisa Metal Çerçeve Siyah | — | homepage | — |
| 381 | 4  Al 3 Öde | — | homepage | — |
| 382 | 500 TL | — | homepage | — |
| 383 | 1.798,99 TL | — | homepage | — |
| 384 | 1.498,99 TL | — | homepage | — |
| 385 | 7.361 TL | — | homepage | — |
| 386 | 6.404,07 TL | — | homepage | — |
| 387 | 676,19 TL | — | homepage | — |
| 388 | 581,52 TL | — | homepage | — |
| 389 | 2  Al 1 Öde | — | homepage | — |
| 390 | 6.999 TL | — | homepage | — |
| 391 | 6.199 TL | — | homepage | — |
| 392 | Lansmana Özel Fırsatlarla Bedava Kargo | — | homepage | — |
| 393 | 3.989,30 TL | — | homepage | — |
| 394 | 3.670,16 TL | — | homepage | — |
| 395 | 2.799 TL | — | homepage | — |
| 396 | 2.575,08 TL | — | homepage | — |
| 397 | 3.599 TL | — | homepage | — |
| 398 | 3.311,08 TL | — | homepage | — |
| 399 | 3.399,15 TL | — | homepage | — |
| 400 | 3.127,22 TL | — | homepage | — |
| 401 | 4.499 TL | — | homepage | — |
| 402 | 4.139,08 TL | — | homepage | — |
| 403 | 2.999 TL | — | homepage | — |
| 404 | 2.759,08 TL | — | homepage | — |
| 405 | 2.499 TL | — | homepage | — |
| 406 | 2.299,08 TL | — | homepage | — |
| 407 | 1.767,32 TL | — | homepage | — |
| 408 | 1.625,93 TL | — | homepage | — |
| 409 | 3.299 TL | — | homepage | — |
| 410 | 3.035,08 TL | — | homepage | — |
| 411 | 3.199,20 TL | — | homepage | — |
| 412 | 2.943,26 TL | — | homepage | — |
| 413 | 509 TL | — | homepage | — |
| 414 | 381,75 TL | — | homepage | — |
| 415 | 639,20 TL | — | homepage | — |
| 416 | Karaca  Frida Katlı Kurabiyelik Kırmızı | — | homepage | — |
| 417 | 899,98 TL | — | homepage | — |
| 418 | 909,99 TL | — | homepage | — |
| 419 | 1.399,90 TL | — | homepage | — |
| 420 | 499 TL | — | homepage | — |
| 421 | 975,90 TL | — | homepage | — |
| 422 | 243,02 TL | — | homepage | — |
| 423 | 223,58 TL | — | homepage | — |
| 424 | 1,500 TL ye %10 indirim | — | homepage | — |
| 425 | 899,99 TL | — | homepage | — |
| 426 | 799,99 TL | — | homepage | — |
| 427 | Sinbo Shb-7503 Şarjlı Kablosuz Rondo | — | homepage | — |
| 428 | 10,000 TL ye 500 TL indirim | — | homepage | — |
| 429 | 519,90 TL | — | homepage | — |
| 430 | Annelere Özel 1.000 TL Altı Hediyeler | — | homepage | — |
| 431 | Yorumlarını Merak Ediyoruz | — | homepage | — |
| 432 | 5.999 TL | — | homepage | — |
| 433 | 5.568,56 TL | — | homepage | — |
| 434 | 464,80 TL | — | homepage | — |
| 435 | 441,56 TL | — | homepage | — |
| 436 | 649,33 TL | — | homepage | — |
| 437 | 584,40 TL | — | homepage | — |
| 438 | Casio MTP-V005D-2B5UDF Erkek Kol Saati | — | homepage | — |
| 439 | 1.957,11 TL | — | homepage | — |
| 440 | 1.869,15 TL | — | homepage | — |
| 441 | Pritt Stick 43 gr 2'li Yapıştırıcı | — | homepage | — |
| 442 | 162 TL | — | homepage | — |
| 443 | 121,50 TL | — | homepage | — |
| 444 | 5.811,61 TL | — | homepage | — |
| 445 | 902,50 TL | — | homepage | — |
| 446 | 874 TL | — | homepage | — |
| 447 | 5  Al 4 Öde | — | homepage | — |
| 448 | 799,90 TL | — | homepage | — |
| 449 | 399,95 TL | — | homepage | — |
| 450 | 2.099 TL | — | homepage | — |
| 451 | 1.931,08 TL | — | homepage | — |
| 452 | 1.450 TL | — | homepage | — |
| 453 | 1.290,50 TL | — | homepage | — |
| 454 | 2.499,99 TL | — | homepage | — |
| 455 | 970 TL | — | homepage | — |
| 456 | 1.487 TL | — | homepage | — |
| 457 | 1.428,81 TL | — | homepage | — |
| 458 | Spor Ayakkabı | — | homepage | — |
| 459 | Kamp Sandalyesi | — | homepage | — |
| 460 | Elektrikli Bisiklet | — | homepage | — |
| 461 | Araç Kamerası | — | homepage | — |
| 462 | Online Alışverişin Adresi n11 | — | homepage | — |
| 463 | abiye elbise | — | homepage | — |
| 464 | Teknoloji Dünyasına Dair Aradıkların... | — | homepage | — |
| 465 | Televizyon ve Ses Sistemleri | — | homepage | — |
| 466 | Yaşam Alanının Tüm İhtiyaçları | — | homepage | — |
| 467 | bahçe mobilyaları | — | homepage | — |
| 468 | Bebeğin ve Senin İçin Her Şey | — | homepage | — |
| 469 | Kişisel Bakım ve Güzellik Ürünleri | — | homepage | — |
| 470 | Şişme bebek | — | homepage | — |
| 471 | Aradığın Spor ve Outdoor Ekipmanları | — | homepage | — |
| 472 | kamp çadırı | — | homepage | — |
| 473 | Oto Lastik | — | homepage | — |
| 474 | motor kaskı | — | homepage | — |
| 475 | GPS cihazlarından | — | homepage | — |
| 476 | Devamını Göster... | — | homepage | — |
| 477 | Dizüstü Bilgisayar | — | pdp | — |
| 478 | Apple Dizüstü Bilgisayar | — | pdp | — |
| 479 | Tüm Özellikler | — | pdp | — |
| 480 | 5.0 | — | pdp | — |
| 481 | Bu üründen 30 UçUç Puan kazanabilirsin. | — | pdp | — |
| 482 | 30 UçUç Puan | — | pdp | — |
| 483 | Distribütör Garantili | — | pdp | — |
| 484 | Marka | — | pdp | — |
| 485 | Ekran Boyutu | — | pdp | — |
| 486 | Ekran Kartı Belleği | — | pdp | — |
| 487 | Diğer | — | pdp | — |
| 488 | Renk | — | pdp | — |
| 489 | Gök Mavisi | — | pdp | — |
| 490 | Sepete Ekle | — | pdp | — |
| 491 | Teslimat Bilgileri | — | pdp | — |
| 492 | DHL e-Commerce - Ücretsiz Kargo | — | pdp | — |
| 493 | Erdem-Teknoloji | — | pdp | — |
| 494 | 10 | — | pdp | — |
| 495 | Mağazaya Sor | — | pdp | — |
| 496 | Takip Et | — | pdp | — |
| 497 | Ürün Rozetleri | — | pdp | — |
| 498 | emrebilism45 10 | — | pdp | — |
| 499 | emrebilism45 | — | pdp | — |
| 500 | SEPETTE
                 45.530,10 TL | — | pdp | — |
| 501 | RSCbilisim 10 | — | pdp | — |
| 502 | RSCbilisim | — | pdp | — |
| 503 | SEPETTE
                 45.539,01 TL | — | pdp | — |
| 504 | PivotExpert 10 | — | pdp | — |
| 505 | PivotExpert | — | pdp | — |
| 506 | SEPETTE
                 46.034,01 TL | — | pdp | — |
| 507 | Ürün Açıklaması | — | pdp | — |
| 508 | Ödeme Kolaylıkları | — | pdp | — |
| 509 | Ürün Bilgileri | — | pdp | — |
| 510 | Optik Sürücü Yok | — | pdp | — |
| 511 | Optik Sürücü | — | pdp | — |
| 512 | Yok | — | pdp | — |
| 513 | Var | — | pdp | — |
| 514 | Garanti Türü Distribütör Garantili | — | pdp | — |
| 515 | Garanti Türü | — | pdp | — |
| 516 | İşlemci Çekirdek Sayısı Diğer | — | pdp | — |
| 517 | İşlemci Çekirdek Sayısı | — | pdp | — |
| 518 | Disk Türü SSD | — | pdp | — |
| 519 | Disk Türü | — | pdp | — |
| 520 | SSD | — | pdp | — |
| 521 | Bellek Türü Diğer | — | pdp | — |
| 522 | Bellek Türü | — | pdp | — |
| 523 | Ekran Kartı Modeli Apple M4 | — | pdp | — |
| 524 | Ekran Kartı Modeli | — | pdp | — |
| 525 | Apple M4 | — | pdp | — |
| 526 | Bellek Kapasitesi 16 GB | — | pdp | — |
| 527 | Bellek Kapasitesi | — | pdp | — |
| 528 | 16 GB | — | pdp | — |
| 529 | İşlemci Modeli Apple M4 | — | pdp | — |
| 530 | İşlemci Modeli | — | pdp | — |
| 531 | İşlemci Apple M4 | — | pdp | — |
| 532 | İşlemci | — | pdp | — |
| 533 | Daha Fazla Bilgi | — | pdp | — |
| 534 | Ürün Değerlendirmeleri | — | pdp | — |
| 535 | 5 | — | pdp | — |
| 536 | 18
            Değerlendirme | — | pdp | — |
| 537 | 18 | — | pdp | — |
| 538 | 12
            Yorum | — | pdp | — |
| 539 | 12 | — | pdp | — |
| 540 | 16.10.2025 | — | pdp | — |
| 541 | n11 üyesi | — | pdp | — |
| 542 | 1 | — | pdp | — |
| 543 | 26.09.2025 | — | pdp | — |
| 544 | Mükemmel bir cihaz. Tavsiye ederim. | — | pdp | — |
| 545 | 09.09.2025 | — | pdp | — |
| 546 | Diğer Mağazalar | — | pdp | — |
| 547 | Mağaza Sıralaması Nasıl Yapılıyor? | — | pdp | — |
| 548 | Alışveriş Kredisi | — | pdp | — |
| 549 | Taksit Seçenekleri    
                    4.714,26 TL'den başlayan taksitlerle | — | pdp | — |
| 550 | Taksit Seçenekleri | — | pdp | — |
| 551 | 4.714,26 TL'den başlayan taksitlerle | — | pdp | — |
| 552 | Mağazaların Seçtiği Ürünler | — | pdp | — |
| 553 | REKLAM | — | pdp | — |
| 554 | 60.000 TL | — | pdp | — |
| 555 | 45.355,26 TL | — | pdp | — |
| 556 | 65.000 TL | — | pdp | — |
| 557 | 56.459,06 TL | — | pdp | — |
| 558 | 46.499 TL | — | pdp | — |
| 559 | 46.034,01 TL | — | pdp | — |
| 560 | 49.035,06 TL | — | pdp | — |
| 561 | 48.931,06 TL | — | pdp | — |
| 562 | 90.286,23 TL | — | pdp | — |
| 563 | 90.094,75 TL | — | pdp | — |
| 564 | 69.999 TL | — | pdp | — |
| 565 | 53.165,56 TL | — | pdp | — |
| 566 | 64.299 TL | — | pdp | — |
| 567 | 63.656,01 TL | — | pdp | — |
| 568 | 34.298 TL | — | pdp | — |
| 569 | 55.637 TL | — | pdp | — |
| 570 | 55.519 TL | — | pdp | — |
| 571 | 42.425,57 TL | — | pdp | — |
| 572 | 42.335,59 TL | — | pdp | — |
| 573 | 63.987,06 TL | — | pdp | — |
| 574 | 63.647,06 TL | — | pdp | — |
| 575 | 68.499 TL | — | pdp | — |
| 576 | 67.814,01 TL | — | pdp | — |
| 577 | 31.626,15 TL | — | pdp | — |
| 578 | 6.399 TL | — | pdp | — |
| 579 | 6.335,01 TL | — | pdp | — |
| 580 | 5.368,09 TL | — | pdp | — |
| 581 | 11.279,06 TL | — | pdp | — |
| 582 | Apple MAGIC MK2E3TU/A Mouse | — | pdp | — |
| 583 | 5.499 TL | — | pdp | — |
| 584 | 5.004,09 TL | — | pdp | — |
| 585 | 26.999 TL | — | pdp | — |
| 586 | 24.569,09 TL | — | pdp | — |
| 587 | 6.119,17 TL | — | pdp | — |
| 588 | 6.096,09 TL | — | pdp | — |
| 589 | 4.752 TL | — | pdp | — |
| 590 | 4.324,32 TL | — | pdp | — |
| 591 | 936,54 TL | — | pdp | — |
| 592 | 36.499 TL | — | pdp | — |
| 593 | 36.134,01 TL | — | pdp | — |
| 594 | 23.625,27 TL | — | pdp | — |
| 595 | 21.499 TL | — | pdp | — |
| 596 | 299 TL | — | pdp | — |
| 597 | 272,09 TL | — | pdp | — |
| 598 | 24.517,06 TL | — | pdp | — |
| 599 | 24.465,06 TL | — | pdp | — |
| 600 | 31.438 TL | — | pdp | — |
| 601 | 29.583,16 TL | — | pdp | — |
| 602 | 31.522,56 TL | — | pdp | — |
| 603 | 26.111,81 TL | — | pdp | — |
| 604 | 22.172,76 TL | — | pdp | — |
| 605 | 22.125,73 TL | — | pdp | — |
| 606 | 33.999 TL | — | pdp | — |
| 607 | 33.659,01 TL | — | pdp | — |
| 608 | 23.574,99 TL | — | pdp | — |
| 609 | 23.524,99 TL | — | pdp | — |
| 610 | 14.630 TL | — | pdp | — |
| 611 | 13.766,83 TL | — | pdp | — |
| 612 | 29.999,99 TL | — | pdp | — |
| 613 | 28.229,99 TL | — | pdp | — |
| 614 | 14.099 TL | — | pdp | — |
| 615 | 13.267,16 TL | — | pdp | — |
| 616 | 26.000 TL | — | pdp | — |
| 617 | 24.466 TL | — | pdp | — |
| 618 | 24.976 TL | — | pdp | — |
| 619 | 23.502,42 TL | — | pdp | — |
| 620 | Daha Fazla Elektronik | — | pdp | — |
| 621 | Daha Fazla Bilgisayar | — | pdp | — |
| 622 | Daha Fazla Dizüstü Bilgisayar | — | pdp | — |
| 623 | 45.499 TL | — | pdp | — |
| 624 | Hemen Al | Buy now | PDP | CTA secondary |
| 625 | Stokta | In stock | PDP | Stock indicator |
| 626 | Tükendi | Out of stock | PDP | Stock indicator |
| 627 | Kargo Bedava | Free shipping | Listing card | Badge |
| 628 | Önceki | Previous | Listing | Pagination |
| 629 | Sonraki | Next | Listing | Pagination |
| 630 | Sepetim Boş | My cart is empty | Cart (empty) | Empty state |
| 631 | Siparişi Tamamla | Complete order | Cart | CTA primary |
| 632 | Siparişlerim | My orders | Account | Nav |
| 633 | Hesabım | My account | Header | Right cluster |
| 634 | Sepetim | My cart | Header | Right cluster |
| 635 | Açıklama | Description | PDP | Tab |
| 636 | Özellikler | Features | PDP | Tab |
| 637 | Kargo | Shipping | PDP | Tab |
| 638 | Ürün Detayı | Product detail | PDP | Heading |
| 639 | Sözleşmeler | Agreements | Footer | Link |
| 640 | Kapıda Ödeme | Cash on delivery | Checkout | Payment option |
| 641 | Kredi Kartı | Credit card | Checkout | Payment option |
| 642 | Adres | Address | Checkout | Form heading |
| 643 | Sipariş Özeti | Order summary | Cart / Checkout | Section |
| 644 | Yapay Zeka Alışveriş Asistanı | AI shopping assistant | n/a (we add) | Phase 11 chat label |

## 3. Category Taxonomy

n11 top-level categories (locked in CLAUDE.md and REQUIREMENTS.md PROD-03):

| Slug | Turkish Label | Sub-categories observed in recon (2-3 per top-level) |
|------|---------------|------------------------------------------------------|
| elektronik | Elektronik | Telefon, Bilgisayar, Tablet |
| moda | Moda | Kadın, Erkek, Çocuk |
| ev-yasam | Ev & Yaşam | Mobilya, Mutfak, Aydınlatma |
| anne-bebek | Anne & Bebek | Bebek Bakım, Oyuncak, Gıda |
| kozmetik | Kozmetik | Cilt Bakım, Makyaj, Parfüm |
| spor-outdoor | Spor & Outdoor | Fitness, Kamp, Bisiklet |
| supermarket | Süpermarket | Kahvaltılık, İçecek, Temizlik |
| kitap-muzik-film-oyun | Kitap, Müzik, Film, Oyun | Kitap, Konsol Oyun, Müzik CD |

> Sub-categories above are *expected* and need recon confirmation. The Playwright spec for the homepage hovers the category mega-menu and captures sub-category names from the dropdown.

## 4. Color Token Table

> Hex values converted from `getComputedStyle()` `rgb(...)` returns. Phase 10 pastes these into `frontend/src/index.css` `@theme` block.

| Token | Hex | Source page | Computed source (rgb) |
|-------|-----|-------------|-----------------------|
| --color-body-bg | #000000 | account | rgb(0, 0, 0) |
| --color-body-bg-bg | #FFFFFF | account | rgb(255, 255, 255) |
| --color-link | #000000 | account | rgb(0, 0, 0) |
| --color-body-bg | #000000 | cart | rgb(0, 0, 0) |
| --color-body-bg-bg | #EDEFF3 | cart | rgb(237, 239, 243) |
| --color-link | #000000 | cart | rgb(0, 0, 0) |
| --color-body-bg | #000000 | category-elektronik | rgb(0, 0, 0) |
| --color-body-bg-bg | #EDEFF3 | category-elektronik | rgb(237, 239, 243) |
| --color-link | #000000 | category-elektronik | rgb(0, 0, 0) |
| --color-body-bg | #000000 | checkout-step1 | rgb(0, 0, 0) |
| --color-body-bg-bg | #EDEFF3 | checkout-step1 | rgb(237, 239, 243) |
| --color-link | #000000 | checkout-step1 | rgb(0, 0, 0) |
| --color-body-bg | #000000 | homepage | rgb(0, 0, 0) |
| --color-body-bg-bg | #EDEFF3 | homepage | rgb(237, 239, 243) |
| --color-link | #000000 | homepage | rgb(0, 0, 0) |
| --color-heading-primary | #000000 | homepage | rgb(0, 0, 0) |
| --color-body-bg | #000000 | login | rgb(0, 0, 0) |
| --color-body-bg-bg | #FFFFFF | login | rgb(255, 255, 255) |
| --color-link | #000000 | login | rgb(0, 0, 0) |
| --color-body-bg | #000000 | pdp | rgb(0, 0, 0) |
| --color-body-bg-bg | #EDEFF3 | pdp | rgb(237, 239, 243) |
| --color-cta-primary | #FFFFFF | pdp | rgb(255, 255, 255) |
| --color-cta-primary-bg | #1C1C1E | pdp | rgb(28, 28, 30) |
| --color-link | #000000 | pdp | rgb(0, 0, 0) |
| --color-heading-primary | #1C1C1E | pdp | rgb(28, 28, 30) |

## 5. Typography Notes

| Property | Observed |
|----------|----------|
| Font family (body) | "Open Sans", Arial, Helvetica, sans-serif |
| Font family (heading) | "Open Sans", Arial, Helvetica, sans-serif |
| Heading size (h1) | 16px |
| Body size | 16px |
| Body font weight | 400 |
| Font weights observed | 400 (body), 500 (subhead), 700 (heading, price) |
| Line height (body) | <recon will confirm — typically 1.4-1.6> |

## 6. Layout Patterns

> Short observations. Phase 10 uses these for grid component decisions.

- **Header:** sticky on scroll, ~60px tall. Left = logo. Center = search bar (full-width). Right cluster = "Hesabım" + "Sepetim" with item-count badge.
- **Category nav:** secondary horizontal bar below header, mega-menu on hover.
- **Listing grid:** 4 columns at desktop (≥1280px), 3 at tablet, 2 at mobile. Card aspect ratio ~1:1.3 (taller than square, room for image + title + price + CTA).
- **PDP:** left = image gallery (~50%), right = product info (title, price, taksit, CTA, stock badge). Tabs ("Açıklama / Özellikler / Kargo") below the fold.
- **Cart:** line items table on left, "Sipariş Özeti" sticky on right.
- **Footer:** 4 columns of help links + payment-method icon strip.

## 7. Anti-pattern flags (what we will NOT copy)

- **No floating chat panel observed.** *Pitfall #19 callout: Phase 11 must invent the chat-bubble UX from scratch. Reference inspiration: ChatGPT widget, Intercom, or Discord — not n11.*
- **Dark-pattern banners** (e.g., countdown timers, "X kişi şu anda görüntülüyor"): if observed, do NOT replicate. Bootcamp grading rewards clean UX.
- **Autoplay video on PDP:** if observed, do NOT replicate.
- **Newsletter pop-ups:** if observed, do NOT replicate.
- **Cookie banner UX:** n11's banner is dismissible-only (no granular consent). Phase 10 ships a simpler "Accept" / "Decline" pair if a banner is needed at all (out of scope for FE-13).

## 8. Open n11 questions for Phase 10 / 11

- Does the PDP show a "Son N ürün!" stock indicator? (Drives PROD-06 implementation copy.)
- Is "Kapıda Ödeme" a visible payment method on the checkout step? (Drives Pitfall mitigation: ship as no-op or disabled radio per FE-V2-03.)
- Does n11 use breadcrumbs on PDP? (Drives FE-07 layout.)
- What's the "Çok Satanlar" rail item count on the homepage? (Drives FE-05 fixture size.)

> Phase 10 plan resolves these from the captured screenshots, not from n11.com directly.
