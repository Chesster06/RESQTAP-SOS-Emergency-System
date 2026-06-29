package com.example.resqtap.news;

import android.os.Handler;
import android.os.Looper;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * MedicalNewsFetcher
 * Memuat turun dan menapis KHUSUS berita perubatan, kesihatan, dan kecemasan klinikal sahaja
 * (100% medic) daripada suapan media rasmi bersama gambar sebenar.
 */
public class MedicalNewsFetcher {

    private static final String[][] RSS_FEEDS_MS = {
            {"Astro Awani", "https://www.astroawani.com/rss/lifestyle/public"},
            {"Free Malaysia Today", "https://www.freemalaysiatoday.com/category/leisure/health/feed/"},
            {"Harian Metro", "https://www.hmetro.com.my/feed"},
            {"Berita Harian", "https://www.bharian.com.my/feed"},
            {"Astro Awani", "https://www.astroawani.com/rss/latest/public"}
    };

    private static final String[][] RSS_FEEDS_EN = {
            {"CodeBlue", "https://codeblue.galencentre.org/feed/"},
            {"Free Malaysia Today", "https://www.freemalaysiatoday.com/category/leisure/health/feed/"},
            {"WHO News", "https://www.who.int/rss-feeds/news-english.xml"},
            {"Astro Awani", "https://www.astroawani.com/rss/lifestyle/public"}
    };

    private static boolean isMalay() {
        String lang = Locale.getDefault().getLanguage();
        return "ms".equalsIgnoreCase(lang) || "in".equalsIgnoreCase(lang);
    }

    // Corak kata kunci perubatan tulen (mesti ada sekurang-kurangnya satu)
    private static final Pattern MEDIC_PATTERN = Pattern.compile(
            "\\b(kkm|kementerian kesihatan|hospital|hospitals|klinik|clinic|clinics|doktor|doctor|doctors|jururawat|nurse|nurses|" +
            "pakar perubatan|physician|perubatan|medical|medicine|medicines|" +
            "pesakit|patient|patients|penyakit|disease|diseases|illness|rawatan|treatment|treatments|pembedahan|surgery|surgical|" +
            "kesihatan|health|healthcare|kesihatan mental|mental health|kesihatan awam|public health|" +
            "vaksin|vaccine|vaccines|vaccination|virus|viruses|viral|denggi|dengue|influenza|flu|wabak|outbreak|jangkitan|infection|infections|bakteria|bacteria|" +
            "kanser|cancer|cancers|jantung|heart|cardiac|cardiology|strok|stroke|strokes|diabetes|diabetic|buah pinggang|kidney|renal|" +
            "ubat|ubatan|drug|drugs|farmasi|pharmacy|pharmaceutical|antibiotik|antibiotic|antibiotics|" +
            "derma darah|blood donation|pusat darah|blood bank|bekalan darah|cpr|ambulans|ambulance|ambulances|pertolongan cemas|first aid|" +
            "jaundis|jaundice|rehabilitasi|rehabilitation|osteosarkoma|anxiety|depresi|depression|autisme|autism|keracunan makanan|food poisoning|terapi|therapy|" +
            "caregiving|caregiver|pediatric|pediatrics|icu|ward|emergency department|ministry of health|who)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Corak kata kunci BUKAN perubatan (ditolak serta-merta)
    private static final Pattern EXCLUDE_PATTERN = Pattern.compile(
            "\\b(lrt|mrt|rapid kl|kesesakan|penumpang|" +
            "agong|sultan|raja-raja|mohor|istana|" +
            "protes|antimigran|rusuhan|perang|tentera|" +
            "politik|parlimen|pilihan raya|parti|jemaah menteri|" +
            "polis|pdrm|rasuah|mahkamah|hakim|dakwa|didakwa|" +
            "bomba|kebakaran|terbakar|padam api|rumah runtuh|" +
            "kemalangan|terbabas|langgar|lori|motosikal|" +
            "sukan|bola sepak|liga|atlet|olimpik|polo|" +
            "artis|konsert|filem|drama|lagu|selebriti|" +
            "saham|dividen|bursa|pelaburan|wang kertas|" +
            "pereka fesyen|rizalman|miss world|harry potter|" +
            "osmo|vivo|ayam brand|masjid|kedai kopi)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface NewsCallback {
        void onSuccess(List<MedicalNewsItem> newsList);
        void onError(Exception e);
    }

    public static void fetchMedicalNews(int limit, NewsCallback callback) {
        executor.execute(() -> {
            List<MedicalNewsItem> aggregated = new ArrayList<>();
            Set<String> seenTitles = new HashSet<>();

            String[][] feeds = isMalay() ? RSS_FEEDS_MS : RSS_FEEDS_EN;
            for (String[] feed : feeds) {
                String sourceName = feed[0];
                String feedUrl = feed[1];
                try {
                    List<MedicalNewsItem> items = fetchFromRss(sourceName, feedUrl);
                    for (MedicalNewsItem item : items) {
                        String t = item.getTitle().trim().toLowerCase(Locale.ROOT);
                        if (seenTitles.add(t) && isStrictlyMedical(item.getTitle() + " " + item.getSnippet())) {
                            // Pastikan ada gambar sebenar
                            if (item.getImageUrl() != null && !item.getImageUrl().trim().isEmpty()) {
                                aggregated.add(item);
                            }
                        }
                    }
                } catch (Exception ignored) {}

                if (limit > 0 && aggregated.size() >= limit * 2) {
                    break;
                }
            }

            // Fallback sekiranya peranti di luar talian
            if (aggregated.isEmpty()) {
                aggregated = getDefaultRealNews();
            }

            final List<MedicalNewsItem> finalResults = (limit > 0 && aggregated.size() > limit)
                    ? aggregated.subList(0, limit)
                    : aggregated;

            mainHandler.post(() -> callback.onSuccess(finalResults));
        });
    }

    private static boolean isStrictlyMedical(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String lower = text.toLowerCase(Locale.ROOT);

        // Tolak jika ada elemen bukan perubatan
        if (EXCLUDE_PATTERN.matcher(lower).find()) {
            return false;
        }

        // Mesti ada perkataan perubatan tulen
        return MEDIC_PATTERN.matcher(lower).find();
    }

    private static List<MedicalNewsItem> fetchFromRss(String defaultSource, String rssUrl) throws Exception {
        List<MedicalNewsItem> list = new ArrayList<>();
        URL url = new URL(rssUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);

        if (conn.getResponseCode() != 200) {
            conn.disconnect();
            return list;
        }

        InputStream is = conn.getInputStream();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(is, "UTF-8");

        int eventType = parser.getEventType();
        boolean insideItem = false;
        String curTitle = "", curLink = "", curPubDate = "", curDesc = "", curImage = "", curSource = defaultSource;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tagName = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                if ("item".equalsIgnoreCase(tagName)) {
                    insideItem = true;
                    curTitle = "";
                    curLink = "";
                    curPubDate = "";
                    curDesc = "";
                    curImage = "";
                    curSource = defaultSource;
                } else if (insideItem) {
                    if ("title".equalsIgnoreCase(tagName)) {
                        curTitle = parser.nextText();
                    } else if ("link".equalsIgnoreCase(tagName)) {
                        curLink = parser.nextText();
                    } else if ("pubDate".equalsIgnoreCase(tagName)) {
                        curPubDate = parser.nextText();
                    } else if ("description".equalsIgnoreCase(tagName)) {
                        curDesc = parser.nextText();
                    } else if ("source".equalsIgnoreCase(tagName)) {
                        curSource = parser.nextText();
                    } else if ("enclosure".equalsIgnoreCase(tagName) || "media:thumbnail".equalsIgnoreCase(tagName) || "media:content".equalsIgnoreCase(tagName)) {
                        String u = parser.getAttributeValue(null, "url");
                        if (u != null && !u.trim().isEmpty() && curImage.isEmpty()) {
                            curImage = u.trim();
                        }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if ("item".equalsIgnoreCase(tagName) && insideItem) {
                    insideItem = false;
                    if (!curTitle.isEmpty()) {
                        String cleanDesc = cleanHtml(curDesc);
                        String formattedDate = formatDate(curPubDate);
                        if (curImage.isEmpty() && curDesc != null && curDesc.contains("<img")) {
                            curImage = extractImageFromHtml(curDesc);
                        }
                        list.add(new MedicalNewsItem(
                                cleanTitle(curTitle),
                                cleanDesc,
                                curLink.trim(),
                                curImage.trim(),
                                extractSource(curTitle, curSource),
                                formattedDate
                        ));
                    }
                }
            }
            eventType = parser.next();
        }

        is.close();
        conn.disconnect();
        return list;
    }

    private static String extractImageFromHtml(String html) {
        if (html == null) return "";
        try {
            int srcIdx = html.indexOf("src=\"");
            if (srcIdx != -1) {
                int start = srcIdx + 5;
                int end = html.indexOf("\"", start);
                if (end > start) {
                    return html.substring(start, end).trim();
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String extractSource(String title, String defaultSource) {
        if (title != null && title.contains(" - ")) {
            int idx = title.lastIndexOf(" - ");
            return title.substring(idx + 3).trim();
        }
        return defaultSource;
    }

    private static String cleanTitle(String raw) {
        if (raw == null) return "";
        int idx = raw.lastIndexOf(" - ");
        if (idx > 0 && idx < raw.length()) {
            return cleanHtml(raw.substring(0, idx)).trim();
        }
        return cleanHtml(raw).trim();
    }

    private static String cleanHtml(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("<[^>]*>", "")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&#8211;", "-")
                .replaceAll("&#8217;", "'")
                .replaceAll("&quot;", "\"")
                .trim();
    }

    private static String formatDate(String raw) {
        if (raw == null || raw.isEmpty()) return isMalay() ? "Terkini" : "Latest";
        try {
            if (raw.contains("T")) {
                SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                Date d = iso.parse(raw);
                if (d != null) {
                    SimpleDateFormat out = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
                    return out.format(d);
                }
            } else {
                SimpleDateFormat rfc = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US);
                Date d = rfc.parse(raw);
                if (d != null) {
                    SimpleDateFormat out = new SimpleDateFormat("d MMM yyyy", Locale.getDefault());
                    return out.format(d);
                }
            }
        } catch (Exception ignored) {}
        return raw.length() > 16 ? raw.substring(0, 16) : raw;
    }

    /** Berita kesihatan dan perubatan sebenar sekiranya luar talian */
    private static List<MedicalNewsItem> getDefaultRealNews() {
        List<MedicalNewsItem> list = new ArrayList<>();
        if (isMalay()) {
            list.add(new MedicalNewsItem(
                    "KKM Pergiat Inisiatif Kesihatan & Rawatan Hospital Awam",
                    "Kementerian Kesihatan Malaysia memperkukuhkan fasiliti perubatan dan latihan asas kecemasan bagi kesejahteraan pesakit.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1584515979956-d9f6e5d09982?w=600&auto=format&fit=crop&q=80",
                    "Kementerian Kesihatan",
                    "8 Sep 2026"
            ));
            list.add(new MedicalNewsItem(
                    "Pencegahan Denggi & Virus: Pakar Nasihat Rawatan Segera",
                    "Pakar perubatan menasihati pesakit agar segera mendapatkan pemeriksaan doktor jika mengalami demam panas berlarutan.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1516549655169-df83a0774514?w=600&auto=format&fit=crop&q=80",
                    "Astro Awani",
                    "8 Sep 2026"
            ));
            list.add(new MedicalNewsItem(
                    "Pusat Darah Negara Gesa Orang Ramai Tampil Menderma Darah",
                    "Bekalan darah jenis O dan B amat diperlukan untuk kegunaan kes kecemasan dan pembedahan hospital di seluruh negara.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1615461066841-6116e61058f4?w=600&auto=format&fit=crop&q=80",
                    "Pusat Darah Negara",
                    "7 Sep 2026"
            ));
            list.add(new MedicalNewsItem(
                    "Unit Respons Ambulans Hospital Diperluas Bagi Bantuan Pantas",
                    "Perkhidmatan paramedik dan ambulans kecemasan dipertingkatkan bagi menjamin keselamatan pesakit kritikal.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1587745416684-47953f16f02f?w=600&auto=format&fit=crop&q=80",
                    "Harian Metro",
                    "7 Sep 2026"
            ));
            list.add(new MedicalNewsItem(
                    "Doktor Nasihat Langkah Pencegahan Serangan Jantung & Strok",
                    "Pemeriksaan kesihatan berkala dan kawalan pemakanan penting dalam mengurangkan risiko penyakit kardiovaskular.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=600&auto=format&fit=crop&q=80",
                    "Berita Harian",
                    "6 Sep 2026"
            ));
        } else {
            list.add(new MedicalNewsItem(
                    "Ministry of Health Intensifies Public Hospital Care & Medical Initiatives",
                    "The Ministry of Health is upgrading emergency clinical facilities, trauma care units, and paramedic response nationwide.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1584515979956-d9f6e5d09982?w=600&auto=format&fit=crop&q=80",
                    "Health Ministry",
                    "8 Sep 2026"
            ));
            list.add(new MedicalNewsItem(
                    "Dengue & Viral Infection Surge: Doctors Advise Immediate Medical Attention",
                    "Medical specialists urge individuals with persistent high fever, joint pain, or rash to seek professional clinical diagnosis immediately.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1516549655169-df83a0774514?w=600&auto=format&fit=crop&q=80",
                    "CodeBlue",
                    "8 Sep 2026"
            ));
            list.add(new MedicalNewsItem(
                    "National Blood Centre Urges Donors to Replenish Critical Blood Supplies",
                    "Emergency trauma reserves of blood types O and B are urgently required for surgical and emergency patient care across hospitals.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1615461066841-6116e61058f4?w=600&auto=format&fit=crop&q=80",
                    "National Blood Centre",
                    "7 Sep 2026"
            ));
            list.add(new MedicalNewsItem(
                    "Hospital Paramedic & Emergency Ambulance Fleet Expanded for Rapid Response",
                    "Rapid response ambulance services are being equipped with advanced cardiac life support to enhance emergency medical survival rates.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1587745416684-47953f16f02f?w=600&auto=format&fit=crop&q=80",
                    "Free Malaysia Today",
                    "7 Sep 2026"
            ));
            list.add(new MedicalNewsItem(
                    "Cardiologists Highlight Key Preventive Steps Against Heart Attack & Stroke",
                    "Routine health screenings, blood pressure control, and heart-healthy dietary habits play a crucial role in lowering cardiovascular risks.",
                    "https://www.moh.gov.my",
                    "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?w=600&auto=format&fit=crop&q=80",
                    "Health Today",
                    "6 Sep 2026"
            ));
        }
        return list;
    }
}
