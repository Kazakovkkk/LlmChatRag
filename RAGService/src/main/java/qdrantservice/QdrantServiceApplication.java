package qdrantservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(exclude = {
        org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration.class
})
public class QdrantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(QdrantServiceApplication.class, args);
    }

}
