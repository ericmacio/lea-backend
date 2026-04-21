package emk.ai.chat.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public final class Utils {

    private final static Logger log = LoggerFactory.getLogger(Utils.class);

    public static void writeJsonError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"error\":\"" + message + "\",\"reply\":null}");
    }

    public static List<String> listFilesFromResources(String folder) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        // List all files in resources root
        Resource[] resources = null;
        try {
            resources = resolver.getResources("classpath:" + folder + "/*");
        } catch (IOException e) {
            log.error("Exception when reading resources folder: {}", folder);
            throw new RuntimeException(e);
        }
        return Arrays.stream(resources)
                .map(resource -> folder + "/" + resource.getFilename())
                .toList();
    }


    public static String readFileFromResources(String fileName) {
        try (InputStream in = new ClassPathResource(fileName).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readPdfFileFromResources(String fileName) {
        PDDocument document;
        String pdfStr;
        try (InputStream in = new ClassPathResource(fileName).getInputStream()) {
            document = Loader.loadPDF(in.readAllBytes());
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStr =  pdfStripper.getText(document);
            return pdfStr;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read PDF file from resources: " + e.getMessage());
        }
    }

    public static String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
