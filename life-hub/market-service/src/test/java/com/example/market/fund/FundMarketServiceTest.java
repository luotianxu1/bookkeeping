package com.example.market.fund;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FundMarketServiceTest {
    private static final String RANK = "var rankData = {datas:[\"001234,测试基金A,CSJJA,2026-11-06,1.2345,2.3456,-0.12,0.5,1.2,3.4,5.6,12.3,22,33,8.9,50\",\"001235,测试基金C,CSJJC,2026-11-05,1.2000,2.3000,0,--,---,,4.5,,20,30,,40\"],allRecords:41,pageIndex:2,pageNum:20};";
    private static final String CATALOG = "\uFEFFvar r = [[\"001234\",\"CSJJ\",\"测试基金A\",\"混合型-偏股\",\"CESHIJIJINA\"],[\"001235\",\"CSJJ\",\"测试基金C\",\"混合型-偏股\",\"CESHIJIJINC\"],[\"000001\",\"CSHB\",\"测试货币\",\"货币型-普通货币\",\"CESHIHUOBI\"]];";
    @Test void preservesPublishedDatesReturnsMissingValuesAndPagination() {
        var service = new FundMarketService(url -> RANK);
        var result = service.list("hh", "month", "desc", 2, 20);
        assertEquals(41, result.total()); assertEquals(2, result.page()); assertEquals(2, result.items().size());
        var first = result.items().get(0);
        assertEquals("001234", first.code()); assertEquals("2026-11-06", first.navDate());
        assertEquals(new BigDecimal("1.2345"), first.nav()); assertEquals(new BigDecimal("-0.12"), first.day());
        assertEquals(new BigDecimal("12.3"), first.year()); assertEquals(new BigDecimal("8.9"), first.ytd());
        assertEquals(BigDecimal.ZERO, result.items().get(1).day()); assertNull(result.items().get(1).year());
        assertNull(result.items().get(1).month()); assertNull(result.items().get(1).quarter());
    }
    @Test void validatesBeforeFetchingAndCachesByFilterSortAndPage() {
        var calls = new AtomicInteger(); var requested = new AtomicReference<String>();
        var service = new FundMarketService(url -> { requested.set(url); calls.incrementAndGet(); return RANK; });
        assertThrows(IllegalArgumentException.class, () -> service.list("bad", "year", "desc", 2, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list("all", "bad", "desc", 2, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list("all", "year", "bad", 2, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list("all", "year", "desc", 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.list("all", "year", "desc", 2, 51));
        assertEquals(0, calls.get());
        service.list("zq", "year", "asc", 2, 20); service.list("zq", "year", "asc", 2, 20);
        assertEquals(1, calls.get()); assertTrue(requested.get().contains("ft=zq&sc=1nzf&st=asc&pi=2&pn=20"));
        service.list("gp", "day", "desc", 2, 20); assertEquals(2, calls.get());
    }
    @Test void searchesTheWholeCatalogWithExactCodeFirstAndNoMoneyFunds() {
        var calls = new AtomicInteger();
        var service = new FundMarketService(url -> { calls.incrementAndGet(); return CATALOG; });
        assertTrue(service.search(" ").items().isEmpty()); assertEquals(0, calls.get());
        assertEquals("001235", service.search("001235").items().get(0).code());
        assertEquals(1, service.search("001235").total());
        assertEquals(2, service.search("测试").total());
        assertEquals(2, service.search("csjj").total());
        assertEquals(2, service.search("ceshijijin").total());
        assertEquals(0, service.search("不存在").total()); assertEquals(1, calls.get());
    }
    @Test void distinguishesEmptyResultsFromMalformedOrUnavailableResponses() {
        var service = new FundMarketService(url -> "<html>failed</html>");
        assertThrows(IllegalStateException.class, () -> service.list("all", "year", "desc", 1, 20));
        assertThrows(IllegalStateException.class, () -> service.search("test"));
        assertThrows(IllegalStateException.class, () -> service.parseRanking(RANK + "alert(1)", 2, 20));
        assertThrows(IllegalStateException.class, () -> service.parseRanking(RANK, 1, 20));
        assertThrows(IllegalStateException.class, () -> service.parseRanking("var rankData={datas:[],allRecords:30,pageIndex:1};", 1, 20));
        assertEquals(0, service.parseRanking("var rankData={datas:[],allRecords:0,pageIndex:1};", 1, 20).total());
    }
    @Test void rejectsInvalidCodesBeforeQuoteLookups() {
        var data = org.mockito.Mockito.mock(FundDataService.class);
        var controller = new MarketQuoteController(data);
        assertThrows(IllegalArgumentException.class, () -> controller.fundBase("001234&other=1"));
        assertThrows(IllegalArgumentException.class, () -> controller.fundTrend("ABC"));
        assertThrows(IllegalArgumentException.class, () -> controller.fundPeriodReturns("123"));
        org.mockito.Mockito.verifyNoInteractions(data);
    }
}
