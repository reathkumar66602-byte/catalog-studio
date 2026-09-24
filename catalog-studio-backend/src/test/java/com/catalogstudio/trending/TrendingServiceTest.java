package com.catalogstudio.trending;

import static org.assertj.core.api.Assertions.assertThat;

import com.catalogstudio.trending.dto.TrendingPageResponse;
import com.catalogstudio.trending.entity.TrendingFeedItem;
import com.catalogstudio.trending.entity.TrendingFeedState;
import com.catalogstudio.trending.entity.UserTrendingProduct;
import com.catalogstudio.trending.service.AmazonBestsellersParser;
import com.catalogstudio.trending.service.AmazonSearchParser;
import com.catalogstudio.trending.service.OpenAiMeeshoLookup;
import com.catalogstudio.trending.service.FlipkartSearchParser;
import com.catalogstudio.trending.service.MeeshoSearchParser;
import com.catalogstudio.trending.service.TrendingBatch;
import com.catalogstudio.trending.service.TrendingCatalog;
import com.catalogstudio.trending.service.TrendingFeedStore;
import com.catalogstudio.trending.service.TrendingHit;
import com.catalogstudio.trending.service.TrendingMarketplaceClient;
import com.catalogstudio.trending.service.TrendingService;
import com.catalogstudio.trending.service.TrendingSnapshotStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrendingServiceTest {

    @Test
    void savedPageSkipsMarketplaceLookup() {
        RecordingClient client = new RecordingClient();
        MemorySnapshot snapshots = new MemorySnapshot(List.of(saved(0)));
        TrendingService service = service(client, new MemoryFeed(List.of()), snapshots);

        TrendingPageResponse page = service.products(7L, "flipkart", "kurti-saree-lehenga", null);

        assertThat(page.cached()).isTrue();
        assertThat(page.products()).hasSize(1);
        assertThat(client.calls).isZero();
    }

    @Test
    void nextPageUsesFeedWithoutCallingMarketplaceWhenAlreadyFetched() {
        RecordingClient client = new RecordingClient();
        MemoryFeed feed = new MemoryFeed(many(20));
        MemorySnapshot snapshots = new MemorySnapshot(List.of(saved(0)));
        TrendingService service = service(client, feed, snapshots);

        TrendingPageResponse page = service.products(7L, "FLIPKART", "kurti-saree-lehenga", 1);

        assertThat(page.cached()).isFalse();
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.products()).hasSize(1);
        assertThat(client.calls).isZero();
    }

    @Test
    void firstVisitFetchesAndStoresTenProducts() {
        RecordingClient client = new RecordingClient();
        MemoryFeed feed = new MemoryFeed(List.of());
        MemorySnapshot snapshots = new MemorySnapshot(List.of());
        TrendingService service = service(client, feed, snapshots);

        TrendingPageResponse page = service.products(7L, "Flipkart", "kurti-saree-lehenga", null);

        assertThat(page.cached()).isFalse();
        assertThat(page.products()).hasSize(1);
        assertThat(client.calls).isEqualTo(1);
        assertThat(snapshots.replacedPage).isZero();
    }

    @Test
    void flipkartParserReadsSearchCards() throws Exception {
        String json = """
                {"RESPONSE":{"slots":[{"widget":{"data":{"products":[{"productInfo":{"value":{
                  "id":"ABC123","productBrand":"BIBA","baseUrl":"/biba-kurta/p/itm1",
                  "titles":{"title":"Printed kurta"},
                  "pricing":{"finalPrice":{"value":499},"mrp":{"value":1999}},
                  "rating":{"average":4.3,"reviewCount":12},
                  "media":{"images":[{"url":"https://rukminim1.flixcart.com/image/{@width}/{@height}/x.jpg?q={@quality}"}]}
                }}}]}}}]}}
                """;
        var hits = FlipkartSearchParser.parse(new ObjectMapper().readTree(json));
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).title()).isEqualTo("Printed kurta");
        assertThat(hits.get(0).priceLabel()).isEqualTo("₹499");
        assertThat(hits.get(0).mrpLabel()).isEqualTo("₹1999");
        assertThat(hits.get(0).productUrl()).isEqualTo("https://www.flipkart.com/biba-kurta/p/itm1");
        assertThat(hits.get(0).imageUrl()).contains("/400/400/");
    }

    @Test
    void amazonParserReadsBestsellerCards() {
        String html = """
                <div id="gridItemRoot">
                  <div id="B07QP9PTZP">
                    <a href="/Euronet-LPG/dp/B07QP9PTZP/ref=zg"><img alt="LPG cylinder" src="https://images-eu.ssl-images-amazon.com/images/I/415.jpg"/></a>
                    <div class="_cDEzb_p13n-sc-css-line-clamp-3_g3dy1">LPG cylinder booking</div>
                    <span class="a-icon-alt">4.5 out of 5 stars</span>
                    <span class="a-size-small">910,361</span>
                    <span class="p13n-sc-price">₹199</span>
                  </div>
                </div>
                """;
        var hits = AmazonBestsellersParser.parse(html);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).externalId()).isEqualTo("B07QP9PTZP");
        assertThat(hits.get(0).title()).isEqualTo("LPG cylinder booking");
        assertThat(hits.get(0).priceLabel()).isEqualTo("₹199");
        assertThat(hits.get(0).rating()).isEqualTo("4.5");
        assertThat(hits.get(0).productUrl()).startsWith("https://www.amazon.in/Euronet-LPG/dp/");
    }

    @Test
    void meeshoParserReadsCatalogCards() throws Exception {
        String json = """
                {"catalogs":[{"id":1,"name":"Adrika Kurtis","product_id":"hh4d82","slug":"black-kurti","min_product_price":205,"image":"https://images.meesho.com/a.jpg","sub_sub_category_name":"Kurtis","catalog_reviews_summary":{"average_rating":4.1,"review_count":72}}],"cursor":"next"}
                """;
        var root = new ObjectMapper().readTree(json);
        var hits = MeeshoSearchParser.parse(root);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).priceLabel()).isEqualTo("₹205");
        assertThat(hits.get(0).productUrl()).isEqualTo("https://www.meesho.com/black-kurti/p/hh4d82");
        assertThat(MeeshoSearchParser.cursor(root)).isEqualTo("next");
    }

    @Test
    void catalogMatchesTingilyParentCategories() {
        assertThat(TrendingCatalog.groups()).extracting(group -> group.category().label()).contains(
                "All products",
                "Bags & Footwear",
                "Beauty & Health",
                "Books",
                "Car & Motorbike",
                "Electronics",
                "Grocery",
                "Home & Kitchen",
                "Jewellery & Accessories",
                "Kids & Toys",
                "Kurti, Saree & Lehenga",
                "Lingerie",
                "Men",
                "Musical Instruments",
                "Office Supplies & Stationery",
                "Pet Supplies",
                "Popular",
                "Sports & Fitness",
                "Watches",
                "Women Western"
        );
        var electronics = TrendingCatalog.groups().stream()
                .filter(group -> group.category().key().equals("electronics"))
                .findFirst()
                .orElseThrow();
        assertThat(electronics.children()).extracting(TrendingCatalog.Category::label).contains(
                "Bluetooth Earbuds",
                "Cases & Covers",
                "Computer Accessories",
                "Power Banks"
        );
        assertThat(TrendingCatalog.requireCategory("electronics--bluetooth-earbuds").flipkartQuery())
                .isEqualTo("Bluetooth Earbuds");
        assertThat(TrendingCatalog.requireCategory("men--kurtas").flipkartQuery()).isEqualTo("Men Kurtas");
        assertThat(TrendingCatalog.requireCategory("men--kurtas").amazonNode()).isEqualTo("q:Men Kurtas");
        assertThat(TrendingCatalog.requireCategory("men--shirts").flipkartQuery()).isEqualTo("Men Shirts");
        assertThat(TrendingCatalog.requireCategory("women-western--dresses").flipkartQuery()).isEqualTo("Women Dresses");
        assertThat(TrendingCatalog.requireCategory("kids-toys--frocks-dresses").flipkartQuery()).isEqualTo("Kids Frocks & Dresses");
        assertThat(TrendingCatalog.requireCategory("kurti-saree-lehenga--cotton-kurtis").flipkartQuery()).isEqualTo("Women Cotton Kurtis");
        assertThat(TrendingCatalog.requireCategory("men--men-footwear").flipkartQuery()).isEqualTo("Men Footwear");
    }

    @Test
    void amazonSearchParserReadsResultCards() {
        String html = """
                <div data-asin="B0KURTI123">
                  <h2><a href="/Women-Kurta/dp/B0KURTI123/ref=sr"><span>Printed kurta</span></a></h2>
                  <img src="https://m.media-amazon.com/images/I/41.jpg"/>
                  <span class="a-offscreen">₹499</span>
                  <span class="a-icon-alt">4.2 out of 5 stars</span>
                </div>
                """;
        var hits = AmazonSearchParser.parse(html);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).externalId()).isEqualTo("B0KURTI123");
        assertThat(hits.get(0).title()).isEqualTo("Printed kurta");
        assertThat(hits.get(0).priceLabel()).isEqualTo("₹499");
        assertThat(hits.get(0).productUrl()).contains("/dp/B0KURTI123");
    }

    @Test
    void openAiMeeshoParserKeepsOnlyRealMeeshoLinks() {
        String content = """
                Here are the results:
                [
                  {"title":"Black kurti","price":"₹205","rating":"4.1","reviews":"72","imageUrl":"https://images.meesho.com/images/products/325819282/a.jpg","productUrl":"https://www.meesho.com/black-kurti/p/hh4d82"},
                  {"title":"Invented","price":"₹10","rating":"5","reviews":"1","imageUrl":"https://example.com/x.jpg","productUrl":"https://example.com/not-meesho"}
                ]
                """;
        var hits = OpenAiMeeshoLookup.parse(new ObjectMapper(), content);
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).externalId()).isEqualTo("hh4d82");
        assertThat(hits.get(0).title()).isEqualTo("Black kurti");
        assertThat(hits.get(0).imageUrl()).contains("images.meesho.com");
    }

    @Test
    void openAiMeeshoParserRejectsCategoryEchoAndProductPagePhotos() {
        String content = """
                [
                  {"title":"Watches","price":"₹166","rating":"3.9 out of 5","reviews":"215 Ratings, 71 Reviews","imageUrl":"https://www.meesho.com/watches","productUrl":"https://www.meesho.com/men-black-analog-wrist-watch/p/ab12cd"},
                  {"title":"Watches","price":"₹10","rating":null,"reviews":null,"imageUrl":null,"productUrl":"https://www.meesho.com/watches/p/zzzz"}
                ]
                """;
        var hits = OpenAiMeeshoLookup.parse(new ObjectMapper(), content, "Watches");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).title()).isEqualTo("Men Black Analog Wrist Watch");
        assertThat(hits.get(0).imageUrl()).isNull();
        assertThat(hits.get(0).rating()).isEqualTo("3.9");
        assertThat(hits.get(0).reviewCount()).isEqualTo("215 Ratings, 71 Reviews");
        assertThat(OpenAiMeeshoLookup.catalogImage(
                "https://images.meesho.com/images/products/1003119703/zf70o_512.avif?width=512"))
                .isEqualTo("https://images.meesho.com/images/products/1003119703/zf70o_512.avif?width=512");
        assertThat(OpenAiMeeshoLookup.usableCard(
                "Sapiens book",
                "https://images.meesho.com/images/products/200x200/0gl8c47_1647360130.jpg"))
                .isFalse();
        var repeated = OpenAiMeeshoLookup.dropRepeatedImages(List.of(
                new TrendingHit("a", "Black analog watch", null, "₹100", null, "4.1", "10 Reviews", "https://images.meesho.com/images/products/1/a.avif", "https://www.meesho.com/a/p/a"),
                new TrendingHit("b", "Blue analog watch", null, "₹120", null, "4.0", "8 Reviews", "https://images.meesho.com/images/products/1/a.avif", "https://www.meesho.com/b/p/b")));
        assertThat(repeated.get(0).imageUrl()).isNull();
        assertThat(repeated.get(1).imageUrl()).isNull();
        String raw = """
                {"output_text":"[]","photo":"https://images.meesho.com/images/products/11/a.avif"}
                {"photo":"https://images.meesho.com/images/products/22/b.avif"}
                """;
        var seeded = List.of(
                new TrendingHit("a", "Red rayon kurti", null, "₹149", null, "4.1", "10 Reviews", null, "https://www.meesho.com/red-rayon-kurti/p/a"),
                new TrendingHit("b", "White cotton kurti", null, "₹168", null, "4.2", "8 Reviews", null, "https://www.meesho.com/white-cotton-kurti/p/b"));
        var photos = OpenAiMeeshoLookup.assignPayloadPhotos(seeded, raw);
        assertThat(photos.get(0).imageUrl()).contains("/11/a.avif");
        assertThat(photos.get(1).imageUrl()).contains("/22/b.avif");
        String searchRaw = """
                {"output":[{"type":"web_search_call","results":[
                  {"type":"image_result","image_url":"https://images.meesho.com/images/products/11/red.avif","source_website_url":"https://www.meesho.com/red-rayon-kurti/p/a"},
                  {"type":"image_result","image_url":"https://images.meesho.com/images/products/22/white.avif","source_website_url":"https://www.meesho.com/white-cotton-kurti/p/b"}
                ]}]}
                """;
        var matched = OpenAiMeeshoLookup.assignSearchImages(seeded, searchRaw);
        assertThat(matched.get(0).imageUrl()).contains("/11/red.avif");
        assertThat(matched.get(1).imageUrl()).contains("/22/white.avif");
        assertThat(TrendingCatalog.requireCategory("men--kurtas").meeshoQuery()).isEqualTo("Men Kurtas");
    }

    private static TrendingService service(RecordingClient client, MemoryFeed feed, MemorySnapshot snapshots) {
        TrendingService service = new TrendingService(List.of(client), feed, snapshots);
        service.indexClients();
        return service;
    }

    private static UserTrendingProduct saved(int page) {
        return UserTrendingProduct.builder()
                .marketplace("FLIPKART")
                .categoryKey("kurti-saree-lehenga")
                .pageIndex(page)
                .slot(0)
                .externalId("ABC")
                .title("Printed kurta")
                .priceLabel("₹499")
                .productUrl("https://www.flipkart.com/p")
                .seenAt(Instant.parse("2026-09-23T10:00:00Z"))
                .build();
    }

    private static List<TrendingFeedItem> many(int count) {
        List<TrendingFeedItem> rows = new ArrayList<>();
        for (int rank = 0; rank < count; rank++) {
            rows.add(TrendingFeedItem.builder()
                    .marketplace("FLIPKART")
                    .categoryKey("kurti-saree-lehenga")
                    .rankIndex(rank)
                    .externalId("ID" + rank)
                    .title("Kurta " + rank)
                    .productUrl("https://www.flipkart.com/p/" + rank)
                    .fetchedAt(Instant.now())
                    .build());
        }
        return rows;
    }

    private static final class RecordingClient implements TrendingMarketplaceClient {
        int calls;

        @Override
        public String marketplace() {
            return "FLIPKART";
        }

        @Override
        public TrendingBatch nextBatch(TrendingCatalog.Category category, String cursor, int nextPage) {
            calls++;
            List<TrendingHit> hits = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                hits.add(new TrendingHit(
                        "A" + i, "Kurta " + i, "Brand", "₹100", null, "4.2", "10", "https://img", "https://www.flipkart.com/A" + i
                ));
            }
            return new TrendingBatch(hits, null, nextPage + 1);
        }
    }

    private static final class MemoryFeed extends TrendingFeedStore {
        private List<TrendingFeedItem> rows;

        MemoryFeed(List<TrendingFeedItem> rows) {
            super(null, null);
            this.rows = new ArrayList<>(rows);
        }

        @Override
        public Snapshot read(String marketplace, String categoryKey) {
            TrendingFeedState state = rows.isEmpty() ? null : TrendingFeedState.builder()
                    .marketplace(marketplace)
                    .categoryKey(categoryKey)
                    .nextPage(2)
                    .itemCount(rows.size())
                    .fetchedAt(Instant.now())
                    .build();
            return new Snapshot(rows, state, !rows.isEmpty());
        }

        @Override
        public void reset(String marketplace, String categoryKey) {
            rows = new ArrayList<>();
        }

        @Override
        public Snapshot append(String marketplace, String categoryKey, List<TrendingHit> hits, int fromRank, String cursor, int nextPage, Instant fetchedAt) {
            int rank = fromRank;
            for (TrendingHit hit : hits) {
                rows.add(TrendingFeedItem.builder()
                        .marketplace(marketplace)
                        .categoryKey(categoryKey)
                        .rankIndex(rank++)
                        .externalId(hit.externalId())
                        .title(hit.title())
                        .productUrl(hit.productUrl())
                        .fetchedAt(fetchedAt)
                        .build());
            }
            return read(marketplace, categoryKey);
        }
    }

    private static final class MemorySnapshot extends TrendingSnapshotStore {
        private List<UserTrendingProduct> rows;
        private int replacedPage = -1;

        MemorySnapshot(List<UserTrendingProduct> rows) {
            super(null, null);
            this.rows = rows;
        }

        @Override
        public List<UserTrendingProduct> saved(Long userId, String marketplace, String categoryKey) {
            return rows;
        }

        @Override
        public List<UserTrendingProduct> replace(Long userId, String marketplace, String categoryKey, int page, List<TrendingHit> hits) {
            replacedPage = page;
            rows = List.of(TrendingServiceTest.saved(page));
            return rows;
        }
    }
}
