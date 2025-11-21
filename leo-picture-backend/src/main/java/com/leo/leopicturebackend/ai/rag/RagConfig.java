package com.leo.leopicturebackend.ai.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class RagConfig {

    @Resource
    private EmbeddingModel qWenEmbeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;//SpingStart提供，基于内存的向量存储

    @Value("${app.documents.path}")
    private String documentsPath;

    private List<Document> loadDocuments() {
        List<Document> documents = new ArrayList<>();
        try {
            // 检查外部目录
            Path externalPath = Paths.get(documentsPath);
            log.info("尝试从外部目录加载文档: {}", externalPath.toAbsolutePath());
            if (Files.exists(externalPath)) {
                documents = FileSystemDocumentLoader.loadDocuments(externalPath.toString());
            } else {
                // 回退到 classpath
                documents = ClassPathDocumentLoader.loadDocuments("docs");
            }
        } catch (Exception e) {
            System.err.println("加载文档失败: " + e.getMessage());
        }
        return documents;
    }
    @Bean
    public ContentRetriever contentRetriever() {
        // 创建一个内容检索器

        // 1. 加载文档
        List<Document> documents = loadDocuments();
        if (documents.isEmpty()) {
            log.warn("没有文档可加载，创建空的 ContentRetriever");
            // 返回一个空的 ContentRetriever 避免应用启动失败
            return query -> {
                log.warn("RAG 功能未启用：没有文档数据");
                return new ArrayList<>();
            };
        }
        //2.文档切割一下，每个文档按照段落分割，最大1000字符，每次最多重叠200个字符(就是让两个段落的切片互相有部分头尾的结合，防止切割规则造成的信息割裂)
        DocumentByCharacterSplitter documentByCharacterSplitter =
                new DocumentByCharacterSplitter(1000, 200);

        //3. 自定义文档加载器，将文档转换成向量去转换成向量数据库中
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentByCharacterSplitter)
                //为了提高文档的质量，为每个切割后的文档碎片 TextSegment 添加文档名称作为元信息,file_name是固定写法？
                .textSegmentTransformer(textSegment ->
                        TextSegment.from(textSegment.metadata().getString("file_name")
                                + "\n" + textSegment.text(), textSegment.metadata()))
                //用哪个模型
                .embeddingModel(qWenEmbeddingModel)
                //用哪个向量存储，这里直接拿SpringBoot大哥集成的
                .embeddingStore(embeddingStore)
                .build();
        //加载文档
        ingestor.ingest(documents);
        //4.自定义内容加载器
        EmbeddingStoreContentRetriever contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(qWenEmbeddingModel)
                .maxResults(5)//最多返回5个结果
                .minScore(0.75)//过滤掉分数小于的结果
                .build();
        return contentRetriever;
    }
}

