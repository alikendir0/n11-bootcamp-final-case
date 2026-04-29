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

> Values harvested via `getComputedStyle()` on `body`, `a`, `h1`, and the PDP "Sepete Ekle" CTA button across all 7 captured pages (`tools/recon/output/*-tokens.json`). Phase 10 maps these into the Tailwind 4 `@theme` block.

| Property | Observed |
|----------|----------|
| Font family (body) | "Open Sans", Arial, Helvetica, sans-serif |
| Font family (heading) | "Open Sans", Arial, Helvetica, sans-serif (homepage h1) — narrows to "Open Sans", Arial on PDP h1 (no Helvetica fallback in product detail) |
| Font family (CTA button) | Arial (PDP "Sepete Ekle" button uses a stripped fallback — no "Open Sans" — observed in `pdp-tokens.json`) |
| Heading size (h1) | 16px on homepage; 20px on PDP (product title gets the larger ramp) |
| Body size | 16px (uniform across all 7 pages) |
| Body font weight | 400 |
| Font weights observed | 400 (body, links), 600 (PDP h1 product title), 700 (homepage h1, PDP CTA button "Sepete Ekle") |
| Line height (body) | <recon underfilled — TokenRow schema does not capture `lineHeight`; Phase 10 to read directly from screenshots, default to Tailwind's 1.5 leading-normal as a safe baseline> |
| Hover/focus states | Not harvested (recon captures static computed style only); Phase 10 derives hover ramps from the brand-orange tokens captured on PDP CTA (`#1C1C1E` background → assume ~10% lighten on hover). |
| Font stack discipline note | n11 ships **only** an Open Sans / Arial / Helvetica fallback chain — no custom-host webfont visible in the harvest. Phase 10 ships Open Sans via `@fontsource/open-sans` (npm) to match without depending on n11's CDN. |

## 6. Layout Patterns

> Short observations. Phase 10 uses these for grid component decisions. Confirmed against `homepage-fullpage.png`, `category-elektronik-fullpage.png`, `pdp-fullpage.png`, `cart-fullpage.png`.

- **Header:** sticky on scroll (homepage screenshot top edge confirms). Left = n11 logo. Center = full-width search bar. Right cluster = "Hesabım" + "Sepetim" with item-count badge. Anonymous state shows "Giriş Yap / Üye Ol" instead of "Hesabım".
- **Category nav:** secondary horizontal bar below header with mega-menu on hover. Recon harvested 70+ category labels via the cart-page mega-menu (see §2 phrases 16-95 — `Moda`, `Elektronik`, `Beyaz Eşya`, ...) — taxonomy is wide; Phase 10 picks the 8 top-level slugs from §3 not the full firehose.
- **Listing grid (category page):** 4 columns at desktop 1440px viewport (counted from `category-elektronik-fullpage.png`). Card aspect taller than square — image area + title + price + "SEPETTE" promo badge + free-shipping ribbon. Phase 10 sets responsive breakpoints to 4 / 3 / 2 / 1 with Tailwind 4 defaults.
- **PDP:** left = image gallery (~50%), right = product info column (title H1 20px, price block, taksit options, "Sepete Ekle" black CTA, stock indicator). Tabs strip ("Ürün Açıklaması / Ödeme Kolaylıkları / Ürün Bilgileri / Daha Fazla Bilgi") below the fold. Reviews + "Diğer Mağazalar" + "Mağazaların Seçtiği Ürünler" rails follow vertically.
- **Cart (empty):** Vertical empty-state hero ("Sepetin Boş Görünüyor") + 3-bullet feature strip ("Kuponlarla", "Favori ürünlerini ekle", "Hemen Giriş Yap") — n11's empty-cart layout is single-column, not "items left + summary right" (that pattern only kicks in when cart has line items, which we did not seed). Phase 10 implements the items-left + summary-right pattern for the populated state per Pattern 6.
- **PDP CTA color:** the "Sepete Ekle" button is **black** (`#1C1C1E`) with white text — NOT orange. n11's brand orange shows only in promo badges + price-discount ribbons, not the primary CTA. Phase 10 uses `--color-cta-primary-bg #1C1C1E` (already in §4) for primary buttons.
- **Footer:** 4-column help-link stack ("Müşteriler / Mağazalar / n11.com / İletişim") + payment-method icon strip + social-follow row. Footer copy harvested under §2.
- **Captured at viewport 1440×900** — recon used a fixed desktop viewport. Phase 10 sets responsive breakpoints based on Tailwind 4 defaults.

## 7. Anti-pattern flags (what we will NOT copy)

- **No floating chat panel observed.** *Pitfall #19 callout: Phase 11 must invent the chat-bubble UX from scratch. Reference inspiration: ChatGPT widget, Intercom, or Discord — not n11.* The "n11 Asistan" link in the cart-page footer (§2 phrase 70) navigates to a static help page; it is NOT a live agent panel — Phase 11 ships a true LLM-backed bubble.
- **Dark-pattern observed in homepage:** countdown timer rows (§2 phrases 233-235: `03 / 47 / 37` — hours/minutes/seconds for promotional flash sales). Will NOT be replicated. Phase 10 ships product cards without urgency-counters.
- **Dark-pattern observed in homepage:** "10 günün en düşük fiyatı!" sticky-pricing badge (§2 phrase 240). Will NOT be replicated — bootcamp grading rewards clean UX, not dark fluctuating-price psychology.
- **Coupon-driven UX overload:** homepage shows 5 stacked discount-coupon cards labeled "1.Kupon / 2.Kupon / ... / 5.Kupon" (§2 phrases 306-310). Phase 10 does NOT ship a coupon system (out of scope per REQUIREMENTS Out-of-Scope) — and even if it did, the visual stacking is too noisy.
- **Autoplay video on PDP:** not observed in `pdp-fullpage.png` (PDP is image-gallery + spec-table, no inline video). No anti-pattern action needed.
- **Newsletter pop-ups:** not observed across the 7 captures (cookie-banner dismiss path may have suppressed it). If observed in production, do NOT replicate.
- **Cookie banner UX:** n11's banner is dismissible-only (no granular consent — see §2 phrase 8 "Çerez Ayarları" — single link, no per-category toggles). Phase 10 ships a simpler "Accept" / "Decline" pair if a banner is needed at all (out of scope for FE-13).
- **Promotional-banner overload (category page):** `category-elektronik-fullpage.png` shows 8+ stacked merchant-banner blocks (Grundig, MediaMarkt, Sharge, BUFFLABS, etc.) above the actual product grid. Will NOT be replicated — Phase 10 puts product cards directly under the category H1 with at most one optional banner slot.

## 8. Open n11 questions for Phase 10 / 11

> Baseline (PDP / Checkout / FE-related observations) + recon-discovered carry-forward items (URL drift, capture-quality notes). Phase 10 / Phase 11 plans resolve these from the captured screenshots, not from n11.com directly.

### Baseline (planning-time questions that screenshot evidence may resolve)

- **Does the PDP show a "Son N ürün!" stock indicator?** No "Son" label visible in the harvested phrase JSON for `/urun/...` (`tools/recon/output/pdp-phrases.json`); the captured PDP shows "Stokta" / "Tükendi" only. Phase 10 ships PROD-06 as a binary stock badge (in stock / out of stock); the "Son N ürün!" warning UX is OUT-OF-SCOPE unless a future capture surfaces it.
- **Is "Kapıda Ödeme" a visible payment method on the checkout step?** Recon could not inspect the post-login checkout (we don't authenticate by design). The cart CTA navigated to `/genel/odeme-secenekleri-393251` (an info page), not a checkout form. Phase 5 / Phase 6 plans must resolve this against the bootcamp brief: ship Kapıda Ödeme as a disabled radio button per FE-V2-03 and Pitfall #11 mitigation.
- **Does n11 use breadcrumbs on PDP?** PDP harvest shows "Dizüstü Bilgisayar" / "Apple Dizüstü Bilgisayar" leading the phrase list (§2 phrases 477-478) — this is a category-trail breadcrumb. Phase 10 FE-07 ships breadcrumbs on PDP.
- **What's the "Çok Satanlar" rail item count on the homepage?** "Çok Satanlar" appears in §2 (phrase 67) as a footer "POPÜLER SAYFALAR" link, NOT as a homepage product rail label. The actual homepage rail labels are "Avantajlı Ürünler" (§2 phrase 263) and "Vitrin Kampanyaları" (§2 phrase 289). Phase 10 FE-05 fixture size: count from `homepage-fullpage.png` — the "Avantajlı Ürünler" rail shows ~10-12 product cards before the "Tümünü Gör" CTA.

### Recon-discovered carry-forwards (Plan 02-02 hand-off)

- **Login path canonical form is `/giris-yap` (NOT `/giris`).** RESEARCH guessed `/giris`; actual is `/giris-yap` (returns 200; `/giris` returns 404). Update FE-02 / FE-04 references and the Phase 10 router config. `account.spec.ts` confirms the redirect: `https://www.n11.com/hesabim` → `https://www.n11.com/giris-yap?redirectUrl=/hesabim`.
- **Anonymous-cart "checkout" CTA semantics are quirky.** Pressing the cart-page CTA on an empty cart redirects to `/genel/odeme-secenekleri-393251` (a generic payment-options info page), not a login form. Phase 10 must NOT model this flow — ship our own anonymous-cart-protect-checkout flow that requires login first.
- **Header element-zoom did not capture cleanly.** The homepage `header` element-zoom Playwright locator resolved to a 1px shell before sticky-positioned content settled. Not blocking — the fullpage screenshot conveys header structure. Phase 10 reads `homepage-fullpage.png` directly for header layout.
- **PDP CTA element-zoom captured a 710-byte 1px shell.** The `[class*="product-detail" i], [class*="basket" i]` selector matched a near-zero container on the captured PDP. Not blocking — `pdp-fullpage.png` has the CTA cluster.
- **Mega-menu hover state on homepage is unverified.** Recon hovered `a[href*="elektronik"]` then captured fullpage; the dropdown state in the screenshot needs human verification. If Phase 10 needs the precise mega-menu DOM, escalate to a Locator-based hover with explicit `expect(locator).toBeVisible()` await before the screenshot.

### New questions surfaced by recon

- **Does n11 require login before showing the address-step form?** The cart's "Hemen Giriş Yap" CTA navigates to login on empty cart, suggesting yes. Phase 10's FE-09 multi-step checkout flow should gate the address-step on authentication and surface a "Hemen Giriş Yap" CTA in the cart sidebar.
- **What does n11 display when a category has zero products?** Not observed (every captured category page has products). Phase 4 (PROD-04) ships an empty-state copy following n11 voice — recommend "Bu kategoride ürün bulunamadı" plus a "Diğer kategorilere göz at" CTA.
- **PDP "Mağaza Sıralaması Nasıl Yapılıyor?" (§2 phrase 547) link** — n11 surfaces a multi-merchant ranking explanation. Out of scope for v1 (single-merchant deliverable per REQUIREMENTS Out-of-Scope), but Phase 10's PDP layout should reserve a help-link slot for future multi-merchant signaling.

## Decision Matrix — Frontend Toolchain (Vite SPA vs Next.js 15)

> Referenced by `.planning/PROJECT.md` Key Decisions row for FE-01. Scoring is 1–5 (5 = best). Weights are bootcamp-grading-lens-tuned: code-quality ×3, timeline ×2, recon-evidence ×1, JWT compat ×2, SSE compat ×2, pitfall avoidance ×2, brief literal ×1.

| Criterion | Weight | Vite + React 19 SPA | Next.js 15 (App Router) |
|-----------|--------|---------------------|-------------------------|
| Code-quality signal (clean layering, hooks-first) | ×3 | 5 | 4 |
| 6-day timeline fit | ×2 | 5 | 3 |
| n11-recon-evidence support (does recon show SSR is essential?) | ×1 | 5 | 2 |
| JWT-at-gateway compatibility | ×2 | 5 | 3 |
| AI chat panel SSE consumption | ×2 | 5 | 3 |
| Avoids Pitfall #16/#19/#23 | ×2 | 5 | 4 |
| Bootcamp brief literal: "React.js storefront" | ×1 | 5 | 5 |

**Weighted totals:**
- **Vite + React 19 SPA: 5×3 + 5×2 + 5×1 + 5×2 + 5×2 + 5×2 + 5×1 = 15+10+5+10+10+10+5 = 65**
- Next.js 15: 4×3 + 3×2 + 2×1 + 3×2 + 3×2 + 4×2 + 5×1 = 12+6+2+6+6+8+5 = 45

**Decision:** Vite + React 19 SPA + TypeScript + Tailwind 4 + Zustand 5 + React Router 7 + TanStack Query 5 + react-hook-form + zod.

**Recon evidence supporting the decision:**
1. **n11 has no in-storefront chat panel** (see §7 Anti-pattern flags) — Phase 11's floating-bubble UX is greenfield. Vite SPA owns the DOM cleanly without RSC re-render gymnastics.
2. **n11 PDP is fully client-rendered after initial HTML** (observed in `pdp-fullpage.png` — the listing grid + PDP layout do not require SSR-only data; our deliverable is a graded interview demo, not a public-search-engine-indexed marketplace).
3. **JWT validated only at the gateway** (locked Phase 1, see CLAUDE.md and PROJECT.md) — SSR-side auth would force token-forwarding through the Node runtime. SPA dispatches `Authorization: Bearer <token>` directly from the browser through the api-gateway → service mesh.

**Carry-forward to Phase 10:** API base URL injected via `VITE_API_BASE_URL` env var (no hardcoded `http://localhost:8080` in source — Pitfall #23 prevention). Frontend toolchain install command will be `npm create vite@latest frontend -- --template react-ts` followed by `npm install tailwindcss@4.x @tailwindcss/vite zustand react-router @tanstack/react-query react-hook-form zod @hookform/resolvers`.

**Carry-forward to Phase 11:** Floating chat bubble = greenfield UX. Reference inspiration in `## 7. Anti-pattern flags`. SSE token streaming consumes via native `EventSource` — no RSC streaming needed.
