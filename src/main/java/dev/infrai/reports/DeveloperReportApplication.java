package dev.infrai.reports;

import com.sun.net.httpserver.HttpServer;
import dev.infrai.reports.config.ReportConfig;
import dev.infrai.reports.email.InfraiEmailClient;
import dev.infrai.reports.pdf.PdfReportRenderer;
import dev.infrai.reports.service.ReleaseReportService;
import dev.infrai.reports.web.ReportHttpHandler;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.concurrent.Executors;

public final class DeveloperReportApplication {
    private DeveloperReportApplication() {}

    public static void main(String[] args) throws Exception {
        ReportConfig config = ReportConfig.fromEnvironment(System.getenv());
        InfraiEmailClient email = new InfraiEmailClient(HttpClient.newHttpClient(), config);
        ReleaseReportService service = new ReleaseReportService(new PdfReportRenderer(), email);
        HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
        server.createContext("/reports", new ReportHttpHandler(service));
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("developer report service listening on http://localhost:" + config.port());
    }
}
