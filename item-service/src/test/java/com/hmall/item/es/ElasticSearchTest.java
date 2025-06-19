package com.hmall.item.es;

import cn.hutool.json.JSONUtil;
import com.hmall.item.domain.po.ItemDoc;
import org.apache.http.HttpHost;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

//@SpringBootTest(properties = "spring.profiles.active=local")
public class ElasticSearchTest {

    private RestHighLevelClient client;

    @Test
    void testMatchAll() throws IOException {
        //1.创建request对象
        SearchRequest request = new SearchRequest("items");
        //2.配置request参数
        request.source()
                .query(QueryBuilders.matchAllQuery());
        //3.发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        //4.解析结果
        parseResponseResult(response);
    }

    private static void parseResponseResult(SearchResponse response){
        SearchHits searchHits = response.getHits();
        //4.1总条数
        long total = searchHits.getTotalHits().value;
        //4.2命中的数据
        SearchHit[] hits = searchHits.getHits();
        for (SearchHit hit : hits) {
            //4.2.1获取source结果
            String json = hit.getSourceAsString();
            //4.2.2转为ItemDoc
            ItemDoc doc = JSONUtil.toBean(json, ItemDoc.class);
            //4.3处理高亮结果
            Map<String, HighlightField> hfs = hit.getHighlightFields();
            if(hfs!=null && !hfs.isEmpty()){
                //4.3.1根据高亮名字段获取高亮结果
                HighlightField hf = hfs.get("name");
                //4.3.2获取高亮姐u共，覆盖非高亮结果
                String hfName = hf.getFragments()[0].string();
                doc.setName(hfName);
            }
            System.out.println("doc=" + doc);
        }
    }

    /**
     * 复合查询
     * @throws IOException
     */
    @Test
    void testSearch() throws IOException{
        //创建request对象
        SearchRequest request = new SearchRequest("items");
        //组织dsl参数
        request.source().query(
                QueryBuilders.boolQuery()
                        .must(QueryBuilders.matchQuery("name","脱脂牛奶"))
                        .filter(QueryBuilders.termQuery("brand","德亚"))
                        .filter(QueryBuilders.rangeQuery("price").lte(30000))
        );
        //发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponseResult(response);
    }

    /**
     * 分页和排序查询
     * @throws IOException
     */
    @Test
    void testSortAndPage() throws IOException{
        //模拟前端传递的分页参数
        int pageNo = 1,pageSize = 5;
        //创建request对象
        SearchRequest request = new SearchRequest("items");
        //组织dsl参数
        //query条件
        request.source().query(QueryBuilders.matchAllQuery());
        //分页
        request.source().from((pageNo-1)*pageSize).size(pageSize);
        //排序
        request.source()
                .sort("sold", SortOrder.DESC)
                .sort("price",SortOrder.ASC);
        //发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponseResult(response);
    }

    /**
     * 高亮显示
     */
    @Test
    void testHighLight() throws IOException{
        //创建request对象
        SearchRequest request = new SearchRequest("items");
        //组织dsl参数
        //query条件
        request.source().query(QueryBuilders.matchQuery("name","脱脂牛奶"));
        //高亮条件
        request.source().highlighter(SearchSourceBuilder.highlight().field("name"));

        //发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);
        parseResponseResult(response);
    }

    @Test
    void testAgg() throws IOException{
        //创建request对象
        SearchRequest request = new SearchRequest("items");
        //组织dsl参数
        //分页条件
        request.source().size(0);
        //聚合条件
        String brandAggName = "brandAgg";
        request.source().aggregation(
                AggregationBuilders.terms(brandAggName).field("brand").size(10)
        );
        //发送请求
        SearchResponse response = client.search(request, RequestOptions.DEFAULT);

        //解析结果
        Aggregations aggregations = response.getAggregations();
        //根据聚合名称获取对应的聚合
        Terms brandTerms = aggregations.get(brandAggName);
        //获取buckets
        List<? extends Terms.Bucket> buckets = brandTerms.getBuckets();
        //遍历获取每一个bucket
        for (Terms.Bucket bucket : buckets) {
            System.out.println("brand:"+bucket.getKeyAsString());
            System.out.println("docCount:"+bucket.getDocCount());
        }
    }

    @BeforeEach
    void setUp(){
        client = new RestHighLevelClient(RestClient.builder(
                HttpHost.create("http://192.168.88.130:9200")
        ));
    }

    @AfterEach
    void tearDown() throws IOException {
        if(client != null){
            client.close();
        }
    }

}
