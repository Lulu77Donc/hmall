package com.hmall.item.es;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.service.IItemService;
import org.apache.http.HttpHost;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest(properties = "spring.profiles.active=local")
public class ElasticDocumentTest {

    private RestHighLevelClient client;
    @Autowired
    private IItemService itemService;

    /**
     * 全局修改，新增
     * @throws IOException
     */
    @Test
    void testIndexDoc() throws IOException {
        //0.装备文档数据
        //根据id查询数据库数据
        Item item = itemService.getById(100000011127L);
        //把数据库的数据转为文档数据
        ItemDoc itemDoc = BeanUtil.copyProperties(item, ItemDoc.class);

        //测试全量更新
        itemDoc.setPrice(29000);

        //1.准备request
        IndexRequest request = new IndexRequest("items").id(itemDoc.getId());
        //2.准备请求参数
        request.source(JSONUtil.toJsonStr(itemDoc), XContentType.JSON);
        //3.发送请求
        client.index(request,RequestOptions.DEFAULT);
    }

    @Test
    void testGetDoc() throws IOException {
        //1.准备request
        GetRequest request = new GetRequest("items", "100000011127");
        //2.发送请求
        GetResponse response = client.get(request, RequestOptions.DEFAULT);
        //3.解析相应
        String json = response.getSourceAsString();
        ItemDoc doc = JSONUtil.toBean(json, ItemDoc.class);
        System.out.println(doc);
    }

    @Test
    void testDeleteDoc() throws IOException {
        //1.准备request
        DeleteRequest request = new DeleteRequest("items", "100000011127");
        //2.发送请求
        client.delete(request, RequestOptions.DEFAULT);
    }

    /**
     * 局部修改
     * @throws IOException
     */
    @Test
    void testUpdateDoc() throws IOException {
        //1.准备request
        UpdateRequest request = new UpdateRequest("items", "100000011127");
        //2.准备请求参数
        request.doc(
                "price",25600
        );
        //3.发送请求
        client.update(request, RequestOptions.DEFAULT);
    }

    @Test
    void testBulkDoc() throws IOException {
        int pageNo = 1,pageSize=500;
        while (true) {
            //1.准备文档数据
            Page<Item> page = itemService.lambdaQuery()
                    .eq(Item::getStatus, 1)
                    .page(Page.of(pageNo, pageSize));
            List<Item> records = page.getRecords();
            if (records == null || records.isEmpty()) {
                return;
            }

            //2.准备request
            BulkRequest bulkRequest = new BulkRequest();
            //3.准备请求参数
            for (Item item : records) {
                bulkRequest.add(new IndexRequest("items")
                        .id(item.getId().toString())
                        .source(JSONUtil.toJsonStr(BeanUtil.copyProperties(item, ItemDoc.class)), XContentType.JSON));
            }

            //4.发送请求
            client.bulk(bulkRequest, RequestOptions.DEFAULT);
            //5.翻页
            pageNo++;
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
