package emk.ai.chat.service;

import emk.ai.chat.dto.ChatRequest;
import emk.ai.chat.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatClient chatClient;

    public ChatService(ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel,
                       @Value("${rag.s3.bucket}") String ragBucketName,
                       @Value("${system-prompt.s3.bucket}") String systemPromptBucket) {

        // Get system prompt
        String systemPrompt = Utils.readFileFromResources("prompts/system-prompt.txt");
        log.debug("systemPrompt: {}", systemPrompt);
        // Build Vector Store
        VectorStore vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        // Load documents to Vector Store
        loadDocuments(vectorStore);

        this.chatClient = chatClientBuilder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(vectorStore).build()
                )
                .build();
    }

    public Flux<String> chatStream(ChatRequest request) {

        List<Message> messages = new ArrayList<>();

        for (ChatRequest.MessageDto msg : request.messages()) {
            // Limit message length
            String content = msg.content().substring(0, Math.min(msg.content().length(), 1000));
            if(!containsSystemOverride(content)) { // Skip basic prompt injection
                if ("user".equals(msg.role())) {
                    messages.add(new UserMessage(content));
                } else if ("assistant".equals(msg.role())) {
                    messages.add(new AssistantMessage(content));
                }
            }
        }

        // Limit conversation history.
        int max = 5;
        List<Message> subMessages = messages.size() > max ?
                new ArrayList<>(messages.subList(messages.size() - max, messages.size())) : messages;
        subMessages.forEach(msg -> log.debug("type: {}, text: {}", msg.getMessageType(), msg.getText()));

        return this.chatClient.prompt()
                .messages(subMessages)
                .stream()
                .content();
    }

    private boolean containsSystemOverride(String message) {
        String lower = message.toLowerCase();
        return lower.contains("ignore previous instructions") ||
                lower.contains("system prompt") ||
                lower.contains("jailbreak");
    }

    private void loadDocuments(VectorStore vectorStore) {
        log.info("Loading RAG documents");
        List<Document> documents = getDocuments();
        if (!documents.isEmpty()) {
            log.info("Create Vector Store");
            vectorStore.add(getChunkedDocs(documents));
        }
    }

    private List<Document> getDocuments() {
        List<String> ragFiles = Utils.listFilesFromResources("rag");
        log.info("ragFiles size: {}", ragFiles.size());
        if(ragFiles.isEmpty()) {
            log.debug("No document found in repository");
        }
        List<Document> docs = new ArrayList<>();
        for(String fileName : ragFiles) {
            log.info("Read file content: {}", fileName);
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("charset", StandardCharsets.UTF_8);
            metadata.put("source", fileName);
            String ext = fileName.split("\\.")[1];
            Document doc = switch (ext) {
                case "txt", "json" -> new Document(Utils.readFileFromResources(fileName), metadata);
                case "pdf" -> new Document(Utils.readPdfFileFromResources(fileName), metadata);
                default -> throw new RuntimeException("Bad file extension: " + ext);
            };
            docs.add(doc);
        }
        return docs;
    }

    private List<Document> getChunkedDocs(List<Document> docs) {
        List<Document> chunkedDocuments = null;
        if(docs != null) {
            log.info("Loaded {} document(s) from the resource.", docs.size());
            TextSplitter textSplitter = new TokenTextSplitter();
            chunkedDocuments = textSplitter.apply(docs);
            log.debug("Docs split into {} chunk(s).", chunkedDocuments.size());
        }
        return chunkedDocuments;
    }
}
