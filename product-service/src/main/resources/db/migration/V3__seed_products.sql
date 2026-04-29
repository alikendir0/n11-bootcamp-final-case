-- V3__seed_products.sql
-- 8 top-level categories + 52 Turkish products (PROD-03, PROD-09).
-- ON CONFLICT DO NOTHING for re-run safety (Flyway re-applies = no error).
--
-- B-04 UUID stability: product id column uses HARDCODED UUID literals
-- ('00000000-0000-4000-8000-000000000001' through '...000052').
-- The inventory-service V3__seed_stock.sql in plan 04-02 references these EXACT
-- same UUIDs as product_id (no cross-schema FK — purely a seed data convention).

-- ---- Categories ----
INSERT INTO categories (slug, name_tr, sort_order) VALUES
    ('elektronik',         'Elektronik',             1),
    ('moda',               'Moda',                   2),
    ('ev-yasam',           'Ev & Yaşam',             3),
    ('anne-bebek',         'Anne & Bebek',            4),
    ('kozmetik',           'Kozmetik',               5),
    ('spor-outdoor',       'Spor & Outdoor',         6),
    ('supermarket',        'Süpermarket',            7),
    ('kitap-muzik-film',   'Kitap-Müzik-Film-Oyun',  8)
ON CONFLICT (slug) DO NOTHING;

-- ---- Products: 52 rows distributed across 8 categories ----
-- Each row uses INSERT ... SELECT to resolve category_id from slug.
-- All Turkish copy; SKU pattern <CAT-NNN>; slug pattern <name-lowered-dashes-NNN>.
-- kdv_rate defaults to 20.00 and seller_name defaults to 'n11 Pazaryeri'
-- so the INSERT only overrides the columns listed.

INSERT INTO products (id, sku, name_tr, description_tr, price_gross, category_id, image_urls, slug)
SELECT v.id::uuid, v.sku, v.name_tr, v.description_tr, v.price_gross::numeric, c.id, v.image_urls::text[], v.slug
FROM (VALUES
    -- Elektronik (8 products)
    ('00000000-0000-4000-8000-000000000001', 'ELE-001', 'Akıllı Telefon X100',        'Yüksek performanslı akıllı telefon, 128 GB depolama.',    '18999.90', 'elektronik',       '{"https://placehold.co/600x600?text=Telefon-X100"}',     'akilli-telefon-x100-001'),
    ('00000000-0000-4000-8000-000000000002', 'ELE-002', 'Bluetooth Kulaklık Pro',     'Aktif gürültü engelleme, 30 saat batarya.',               '1299.90',  'elektronik',       '{"https://placehold.co/600x600?text=Kulaklik-Pro"}',     'bluetooth-kulaklik-pro-002'),
    ('00000000-0000-4000-8000-000000000003', 'ELE-003', 'Dizüstü Bilgisayar Air',     '14 inç, 16 GB RAM, 512 GB SSD.',                         '29999.00', 'elektronik',       '{"https://placehold.co/600x600?text=Laptop-Air"}',       'dizustu-bilgisayar-air-003'),
    ('00000000-0000-4000-8000-000000000004', 'ELE-004', 'Akıllı Saat Sport',          'Su geçirmez, kalp ritmi takibi.',                        '2499.00',  'elektronik',       '{"https://placehold.co/600x600?text=Saat-Sport"}',       'akilli-saat-sport-004'),
    ('00000000-0000-4000-8000-000000000005', 'ELE-005', 'Tablet Pro 11"',             'Retina ekran, 256 GB.',                                  '14990.00', 'elektronik',       '{"https://placehold.co/600x600?text=Tablet-Pro"}',       'tablet-pro-005'),
    ('00000000-0000-4000-8000-000000000006', 'ELE-006', 'Kablosuz Şarj Aleti',        '15W hızlı şarj.',                                        '399.90',   'elektronik',       '{"https://placehold.co/600x600?text=Sarj"}',             'kablosuz-sarj-aleti-006'),
    ('00000000-0000-4000-8000-000000000007', 'ELE-007', '4K Akıllı Televizyon 55"',  'HDR, Wi-Fi, sesli kumanda.',                             '21500.00', 'elektronik',       '{"https://placehold.co/600x600?text=TV-55"}',            '4k-akilli-televizyon-55-007'),
    ('00000000-0000-4000-8000-000000000051', 'ELE-008', 'Mekanik Klavye RGB',         'Mavi switch, USB-C.',                                    '1499.00',  'elektronik',       '{"https://placehold.co/600x600?text=Klavye"}',           'mekanik-klavye-rgb-051'),

    -- Moda (8 products)
    ('00000000-0000-4000-8000-000000000008', 'MOD-001', 'Erkek Pamuklu Tişört',       'Slim fit, %100 pamuk.',                                  '199.90',   'moda',             '{"https://placehold.co/600x600?text=Tisort"}',           'erkek-pamuklu-tisort-008'),
    ('00000000-0000-4000-8000-000000000009', 'MOD-002', 'Kadın Triko Kazak',          'Yumuşak yün karışımı.',                                  '549.90',   'moda',             '{"https://placehold.co/600x600?text=Kazak"}',            'kadin-triko-kazak-009'),
    ('00000000-0000-4000-8000-000000000010', 'MOD-003', 'Spor Ayakkabı Runner',       'Hafif tabanlı, koşu için ideal.',                        '1299.00',  'moda',             '{"https://placehold.co/600x600?text=Ayakkabi"}',         'spor-ayakkabi-runner-010'),
    ('00000000-0000-4000-8000-000000000011', 'MOD-004', 'Deri Cüzdan Klasik',         'Hakiki deri, kart bölmeli.',                             '699.00',   'moda',             '{"https://placehold.co/600x600?text=Cuzdan"}',           'deri-cuzdan-klasik-011'),
    ('00000000-0000-4000-8000-000000000012', 'MOD-005', 'Kot Pantolon Slim',          'Streç dokuma.',                                          '899.90',   'moda',             '{"https://placehold.co/600x600?text=Pantolon"}',         'kot-pantolon-slim-012'),
    ('00000000-0000-4000-8000-000000000013', 'MOD-006', 'Yünlü Atkı',                 'Kışlık, soğuğa karşı.',                                  '249.00',   'moda',             '{"https://placehold.co/600x600?text=Atki"}',             'yunlu-atki-013'),
    ('00000000-0000-4000-8000-000000000014', 'MOD-007', 'Güneş Gözlüğü Aviator',     'UV400 koruma.',                                          '799.00',   'moda',             '{"https://placehold.co/600x600?text=Gozluk"}',          'gunes-gozlugu-aviator-014'),
    ('00000000-0000-4000-8000-000000000052', 'MOD-008', 'Kışlık Bot Kadın',           'Sıcak iç astar.',                                       '899.00',   'moda',             '{"https://placehold.co/600x600?text=Bot"}',             'kislik-bot-kadin-052'),

    -- Ev & Yaşam (6 products)
    ('00000000-0000-4000-8000-000000000015', 'EVY-001', 'Çelik Tencere Seti 5 Parça', 'İndüksiyonlu ocaklara uygun.',                          '1899.00',  'ev-yasam',         '{"https://placehold.co/600x600?text=Tencere"}',          'celik-tencere-seti-5-parca-015'),
    ('00000000-0000-4000-8000-000000000016', 'EVY-002', 'Yatak Örtüsü Çift Kişilik', 'Pamuklu kumaş, yıkanabilir.',                            '699.90',   'ev-yasam',         '{"https://placehold.co/600x600?text=Yatak-Ortu"}',       'yatak-ortusu-cift-kisilik-016'),
    ('00000000-0000-4000-8000-000000000017', 'EVY-003', 'Robot Süpürge Akıllı',      'Wi-Fi kontrol, otomatik şarj.',                          '5999.00',  'ev-yasam',         '{"https://placehold.co/600x600?text=Supurge"}',          'robot-supurge-akilli-017'),
    ('00000000-0000-4000-8000-000000000018', 'EVY-004', 'Aydınlatma LED Lambader',   'Ayarlanabilir parlaklık.',                               '449.00',   'ev-yasam',         '{"https://placehold.co/600x600?text=Lambader"}',         'aydinlatma-led-lambader-018'),
    ('00000000-0000-4000-8000-000000000019', 'EVY-005', 'Mutfak Robotu 8 İşlev',     '1500W motor.',                                           '2799.00',  'ev-yasam',         '{"https://placehold.co/600x600?text=Mutfak-Robot"}',     'mutfak-robotu-8-islev-019'),
    ('00000000-0000-4000-8000-000000000020', 'EVY-006', 'Halı Yolluk 80x300 cm',     'Modern desenli, yıkanabilir.',                           '899.00',   'ev-yasam',         '{"https://placehold.co/600x600?text=Hali"}',             'hali-yolluk-80x300-cm-020'),

    -- Anne & Bebek (6 products)
    ('00000000-0000-4000-8000-000000000021', 'AB-001',  'Bebek Bezi Beden 4',        '52 parça paket, üstün emicilik.',                        '499.90',   'anne-bebek',       '{"https://placehold.co/600x600?text=Bebek-Bezi"}',       'bebek-bezi-beden-4-021'),
    ('00000000-0000-4000-8000-000000000022', 'AB-002',  'Biberon Yenidoğan',         'BPA içermez, 240 ml.',                                   '149.00',   'anne-bebek',       '{"https://placehold.co/600x600?text=Biberon"}',          'biberon-yenidogan-022'),
    ('00000000-0000-4000-8000-000000000023', 'AB-003',  'Bebek Arabası Çift Yönlü', 'Hafif alüminyum kasa.',                                  '6999.00',  'anne-bebek',       '{"https://placehold.co/600x600?text=Bebek-Arabasi"}',    'bebek-arabasi-cift-yonlu-023'),
    ('00000000-0000-4000-8000-000000000024', 'AB-004',  'Mama Sandalyesi',           '6 ay+, ayarlanabilir yükseklik.',                        '1899.00',  'anne-bebek',       '{"https://placehold.co/600x600?text=Mama-Sandalyesi"}',  'mama-sandalyesi-024'),
    ('00000000-0000-4000-8000-000000000025', 'AB-005',  'Islak Mendil 3lü Paket',   'Hassas ciltler için.',                                   '89.90',    'anne-bebek',       '{"https://placehold.co/600x600?text=Islak-Mendil"}',     'islak-mendil-3lu-paket-025'),
    ('00000000-0000-4000-8000-000000000026', 'AB-006',  'Bebek Pijama Takımı',       '0-3 ay, organik pamuk.',                                 '249.90',   'anne-bebek',       '{"https://placehold.co/600x600?text=Pijama"}',           'bebek-pijama-takimi-026'),

    -- Kozmetik (6 products)
    ('00000000-0000-4000-8000-000000000027', 'KOZ-001', 'Yüz Nemlendirici Krem',     '50 ml, hyaluronik asit.',                                '299.00',   'kozmetik',         '{"https://placehold.co/600x600?text=Krem"}',             'yuz-nemlendirici-krem-027'),
    ('00000000-0000-4000-8000-000000000028', 'KOZ-002', 'Şampuan Onarıcı 500 ml',    'Boyalı saçlar için.',                                    '149.90',   'kozmetik',         '{"https://placehold.co/600x600?text=Sampuan"}',          'sampuan-onarici-500-ml-028'),
    ('00000000-0000-4000-8000-000000000029', 'KOZ-003', 'Parfüm Kadın Çiçeksi 50 ml','Uzun kalıcı koku.',                                     '999.00',   'kozmetik',         '{"https://placehold.co/600x600?text=Parfum"}',           'parfum-kadin-ciceksi-029'),
    ('00000000-0000-4000-8000-000000000030', 'KOZ-004', 'Saç Kurutma Makinesi',      '2200W, iyonik teknoloji.',                               '1099.00',  'kozmetik',         '{"https://placehold.co/600x600?text=Fon-Makinesi"}',     'sac-kurutma-makinesi-030'),
    ('00000000-0000-4000-8000-000000000031', 'KOZ-005', 'Ruj Mat Bordo',             'Uzun ömürlü, kuruluk yapmaz.',                           '179.00',   'kozmetik',         '{"https://placehold.co/600x600?text=Ruj"}',              'ruj-mat-bordo-031'),
    ('00000000-0000-4000-8000-000000000032', 'KOZ-006', 'Erkek Tıraş Köpüğü',       'Hassas ciltler için, 200 ml.',                           '69.90',    'kozmetik',         '{"https://placehold.co/600x600?text=Kopuk"}',            'erkek-tiras-kopugu-032'),

    -- Spor & Outdoor (6 products)
    ('00000000-0000-4000-8000-000000000033', 'SPO-001', 'Yoga Matı 6 mm',            'Kaymaz yüzey.',                                          '299.00',   'spor-outdoor',     '{"https://placehold.co/600x600?text=Yoga"}',             'yoga-mati-6-mm-033'),
    ('00000000-0000-4000-8000-000000000034', 'SPO-002', 'Çadır 4 Kişilik',           'Su geçirmez, kolay kurulum.',                            '2999.00',  'spor-outdoor',     '{"https://placehold.co/600x600?text=Cadir"}',            'cadir-4-kisilik-034'),
    ('00000000-0000-4000-8000-000000000035', 'SPO-003', 'Bisiklet Kaskı',            'CE sertifikalı, ayarlanabilir.',                         '599.00',   'spor-outdoor',     '{"https://placehold.co/600x600?text=Kask"}',             'bisiklet-kaski-035'),
    ('00000000-0000-4000-8000-000000000036', 'SPO-004', 'Dumbell Set 2x5 kg',        'Kauçuk kaplama.',                                        '449.90',   'spor-outdoor',     '{"https://placehold.co/600x600?text=Dumbell"}',          'dumbell-set-2x5-kg-036'),
    ('00000000-0000-4000-8000-000000000037', 'SPO-005', 'Termal Mont',               'Suya dayanıklı, eksi 10 derece kadar.',                  '1899.00',  'spor-outdoor',     '{"https://placehold.co/600x600?text=Mont"}',             'termal-mont-037'),
    ('00000000-0000-4000-8000-000000000038', 'SPO-006', 'Su Matarası Çelik 750 ml',  'Sıcak/soğuk yalıtımı.',                                 '199.00',   'spor-outdoor',     '{"https://placehold.co/600x600?text=Matara"}',           'su-matarasi-celik-750-ml-038'),

    -- Süpermarket (6 products)
    ('00000000-0000-4000-8000-000000000039', 'SPR-001', 'Zeytinyağı Naturel 1 L',    'Soğuk sıkım, sızma.',                                    '349.00',   'supermarket',      '{"https://placehold.co/600x600?text=Yag"}',              'zeytinyagi-naturel-1-l-039'),
    ('00000000-0000-4000-8000-000000000040', 'SPR-002', 'Kahve Çekirdeği 250 g',     'Orta kavrum, Arabica.',                                  '219.90',   'supermarket',      '{"https://placehold.co/600x600?text=Kahve"}',            'kahve-cekirdegi-250-g-040'),
    ('00000000-0000-4000-8000-000000000041', 'SPR-003', 'Çay Demlik Klasik 500 g',   'Rize karışımı.',                                         '129.00',   'supermarket',      '{"https://placehold.co/600x600?text=Cay"}',              'cay-demlik-klasik-041'),
    ('00000000-0000-4000-8000-000000000042', 'SPR-004', 'Pirinç Baldo 5 kg',         'Pilav için ideal.',                                      '299.00',   'supermarket',      '{"https://placehold.co/600x600?text=Pirinc"}',           'pirinc-baldo-5-kg-042'),
    ('00000000-0000-4000-8000-000000000043', 'SPR-005', 'Süt Tam Yağlı 1 L',         'UHT, uzun ömürlü.',                                       '59.90',    'supermarket',      '{"https://placehold.co/600x600?text=Sut"}',              'sut-tam-yagli-043'),
    ('00000000-0000-4000-8000-000000000044', 'SPR-006', 'Bulaşık Deterjanı 1.5 L',   'Limon kokulu.',                                          '89.90',    'supermarket',      '{"https://placehold.co/600x600?text=Deterjan"}',         'bulasik-deterjani-044'),

    -- Kitap-Müzik-Film-Oyun (6 products)
    ('00000000-0000-4000-8000-000000000045', 'KMF-001', 'Roman: Sessiz Şehir',       'Yerli yazar, çağdaş edebiyat.',                          '179.00',   'kitap-muzik-film', '{"https://placehold.co/600x600?text=Roman"}',            'roman-sessiz-sehir-045'),
    ('00000000-0000-4000-8000-000000000046', 'KMF-002', 'Çocuk Hikaye Kitabı Seti',  '5 kitaplık eğitici set.',                                '299.00',   'kitap-muzik-film', '{"https://placehold.co/600x600?text=Cocuk-Kitap"}',      'cocuk-hikaye-kitabi-seti-046'),
    ('00000000-0000-4000-8000-000000000047', 'KMF-003', 'Vinyl Plak Türk Sanat',     '180 g, sınırlı baskı.',                                  '799.00',   'kitap-muzik-film', '{"https://placehold.co/600x600?text=Plak"}',             'vinyl-plak-turk-sanat-047'),
    ('00000000-0000-4000-8000-000000000048', 'KMF-004', 'PlayStation 5 Oyun Disk',   'Aksiyon-macera oyunu.',                                  '2199.00',  'kitap-muzik-film', '{"https://placehold.co/600x600?text=PS5-Oyun"}',         'playstation-5-oyun-disk-048'),
    ('00000000-0000-4000-8000-000000000049', 'KMF-005', 'Kutu Oyunu Strateji',       '4 kişilik, 90 dakika.',                                  '549.00',   'kitap-muzik-film', '{"https://placehold.co/600x600?text=Oyun"}',             'kutu-oyunu-strateji-049'),
    ('00000000-0000-4000-8000-000000000050', 'KMF-006', 'Film Blu-ray Klasik',       'HD restorasyon.',                                        '249.00',   'kitap-muzik-film', '{"https://placehold.co/600x600?text=Film"}',             'film-bluray-klasik-050')
) AS v(id, sku, name_tr, description_tr, price_gross, category_slug, image_urls, slug)
JOIN categories c ON c.slug = v.category_slug
ON CONFLICT (sku) DO NOTHING;
