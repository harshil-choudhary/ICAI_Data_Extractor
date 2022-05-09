/*
    Author : Harshil Choudhary
*/

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import javax.net.ssl.SSLSession;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Main {

    static String url = "http://220.225.242.182/lom.asp";
    static HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private static CompletableFuture<String[]> icaiSiteDetailsByMembershipNumber(Long membershipNumber) throws URISyntaxException, IOException, InterruptedException {

        // Building URL Param
        StringBuilder membershipNumberInString = new StringBuilder();
        if (membershipNumber < 100000L) {
            int numberOfPrecedingZeroes = 6 - String.valueOf(membershipNumber).length();
            for (int i = 0; i<numberOfPrecedingZeroes; i++) {
                membershipNumberInString.append("0");
            }
            membershipNumberInString.append(membershipNumber);
        } else {
            membershipNumberInString = new StringBuilder(Long.toString(membershipNumber));
        }
        String urlParameter = "t1=" + membershipNumberInString;

        int statusCode = 200;
        String responseBody = "";
        // HTTP Request
        System.out.println("Getting Data for : " + membershipNumberInString);
        do {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .POST(HttpRequest.BodyPublishers.ofString(urlParameter))
                    .headers("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                responseBody = response.body();
                statusCode = response.statusCode();
            } catch (IOException e) {
                System.out.println("IOException for " + membershipNumber);
                statusCode = 500;
            }
            if (statusCode >= 300) {
                System.out.println("Retrying for " + membershipNumber + ". Status : " + statusCode);
            }
        } while (statusCode >= 300);

        String name = "";
        String address = "";

        // JSoup magic begins here
        Document document = Jsoup.parseBodyFragment(responseBody);

        // If page has less than 5 tables it means data is not present
        Elements tables = document.select("table");
        if (tables.size() == 5) {
            // Getting name and address using JSoup
            Element table = document.select("table").get(3);
            Elements rows = table.select("tr");
            name = rows.get(1).select("td").get(1).text();
            name = name.substring(0,name.indexOf(','));             // to remove ", FCA"
            address = rows.get(4).select("td").get(1).text()
                    + " "
                    + rows.get(5).select("td").get(1).text()
                    + " "
                    + rows.get(6).select("td").get(1).text()
                    + " "
                    + rows.get(8).select("td").get(1).text()
                    + " "
                    + rows.get(9).select("td").get(1).text();
        }

        if (address.equals("") || name.equals("")) {
            System.out.println("Received No Data for " + membershipNumberInString);
        } else {
            System.out.println("Received Data : " + membershipNumberInString + " " + name + " " + address);
        }

        return CompletableFuture.completedFuture(new String[]{String.valueOf(membershipNumberInString), name, address});
    }

    private static String convertLineToCSV(String[] data) {
        return String.join(",", data);
    }

    private static void csvBuilder(Long membershipNumberStart, Long membershipNumberEnd) throws URISyntaxException, IOException, InterruptedException {
        List<CompletableFuture<String[]>> allFutures = new ArrayList<>();
        // Compile data from each call
        for (Long i = membershipNumberStart; i<=membershipNumberEnd; i++) {
            allFutures.add(icaiSiteDetailsByMembershipNumber(i));
        }

        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();
        String[] headers = new String[]{"Membership No.", "Name", "Address"};

        // Create CSV
        System.out.println("Compiling CSV...");
        File csvOutputFile = new File("ICAI_Data_" + membershipNumberStart + "_" +membershipNumberEnd + ".csv");

        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            pw.println(convertLineToCSV(headers));
            for (int i = 0; i<allFutures.size(); i++) {
                String s = convertLineToCSV(allFutures.get(i).get());
                pw.println(s);
            }
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    public static void main(String args[]) throws URISyntaxException, IOException, InterruptedException {
        Long membershipNumberStart = 0L;
        Long membershipNumberEnd = 99999L;

        Instant start = Instant.now();
        csvBuilder(membershipNumberStart, membershipNumberEnd);
        System.out.println("Done! Total time: " + Duration.between(start, Instant.now()).getSeconds() + " seconds");
    }
}
