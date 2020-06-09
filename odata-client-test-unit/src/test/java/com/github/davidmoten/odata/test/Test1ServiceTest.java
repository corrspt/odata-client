package com.github.davidmoten.odata.test;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.davidmoten.guavamini.Sets;
import com.github.davidmoten.odata.client.HttpMethod;
import com.github.davidmoten.odata.client.RequestHeader;
import com.github.davidmoten.odata.client.Serializer;

import test1.a.entity.Product;
import test1.b.container.Test1Service;

public class Test1ServiceTest {

    @Test
    public void testCanReferenceEntityFromEntityContainerInAnotherSchema() {
        Test1Service client = Test1Service.test().build();
        client.products(1);
    }

    @Test
    public void testChangedFieldsAreSet() {
        Product p = Product.builder().name("bingo").build();
        assertEquals(Sets.newHashSet("Name"), p.getChangedFields().toSet());
        p = p.withName("joey").withID(124);
        assertEquals(Sets.newHashSet("Name", "ID"), p.getChangedFields().toSet());
    }

    @Test
    public void testFieldChangedToNull() throws JsonProcessingException {
        Product p = Product.builder().ID(12).name("bingo").build();
        Product p2 = p.withName(null);
        // note that changed fields hasn't been reset yet so all fields still appear to
        // have been changed. A unit test for using withXXX on an object returned from 
        // a post is a different matter though (see odata-client-test-unit module).
        assertEquals("{\"@odata.type\":\"Test1.A.Product\",\"ID\":12,\"Name\":null}",
                Serializer.INSTANCE.serializeChangesOnly(p2));
    }

    @Test
    public void testPost() {
        Test1Service client = Test1Service //
                .test() //
                .expectRequest("/Products", "/request-post.json", HttpMethod.POST,
                        RequestHeader.ACCEPT_JSON_METADATA_MINIMAL,
                        RequestHeader.CONTENT_TYPE_JSON_METADATA_MINIMAL,
                        RequestHeader.ODATA_VERSION) //
                .expectResponse("/Products", "/response-post.json", HttpMethod.POST,
                        RequestHeader.ACCEPT_JSON_METADATA_MINIMAL,
                        RequestHeader.CONTENT_TYPE_JSON_METADATA_MINIMAL,
                        RequestHeader.ODATA_VERSION) //
                .build();
        Product p = Product.builder().name("bingo").build();
        Product p2 = client.products().post(p);
        Product p3 = p2.withName(null);
        assertEquals("{\"@odata.type\":\"Test1.A.Product\",\"Name\":null}",
                Serializer.INSTANCE.serializeChangesOnly(p3));
    }

    @Test
    public void testDelete() {
        Test1Service client = Test1Service //
                .test() //
                .expectDelete("/Products/1", RequestHeader.ACCEPT_JSON_METADATA_MINIMAL,
                        RequestHeader.CONTENT_TYPE_JSON_METADATA_MINIMAL,
                        RequestHeader.ODATA_VERSION) //
                .build();
        client.products().id("1").delete();
    }

}
