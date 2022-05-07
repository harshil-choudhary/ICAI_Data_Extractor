/*
    Author : Harshil Choudhary
*/

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class Main {

    static String url = "http://220.225.242.182/lom.asp";

    private static String[] icaiSiteDetailsByMembershipNumber(Long membershipNumber) throws URISyntaxException, IOException, InterruptedException {

        // Building URL Param
        String urlParameter = "t1=" + membershipNumber;

        int statusCode = 200;
        String responseBody = "";
        // HTTP Request
        do {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .POST(HttpRequest.BodyPublishers.ofString(urlParameter))
                    .headers("Content-Type", "application/x-www-form-urlencoded")
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            responseBody = response.body();
            statusCode = response.statusCode();
            if (statusCode >= 300) {
                System.out.println("Retrying for " + membershipNumber + ". Status : " + statusCode);
            }
        } while (statusCode >= 300);

        String membershipNumberString = Long.toString(membershipNumber);
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
        System.out.println("Received Data : " + membershipNumberString + " " + name + " " + address);
        return new String[]{membershipNumberString, name, address};
    }

    private static String convertLineToCSV(String[] data) {
        return String.join(",", data);
    }

    private static void csvBuilder(Long membershipNumberStart, Long membershipNumberEnd) throws URISyntaxException, IOException, InterruptedException {
        // Compile data from each call
        List<String[]> dataLines= new ArrayList<>();
        dataLines.add(new String[]{"Membership No.", "Name", "Address"});
        for (Long i = membershipNumberStart; i<=membershipNumberEnd; i++) {
            System.out.println("Getting Data for : " + i);
            dataLines.add(icaiSiteDetailsByMembershipNumber(i));
        }

        // Create CSV
        System.out.println("Compiling CSV...");
        File csvOutputFile = new File("ICAI_Data_" + membershipNumberStart + "_" +membershipNumberEnd + ".csv");

        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            for (String[] dataLine : dataLines) {
                String s = convertLineToCSV(dataLine);
                pw.println(s);
            }
        }
    }

    public static void main(String args[]) throws URISyntaxException, IOException, InterruptedException {
        Long membershipNumberStart = 100000L;
        Long membershipNumberEnd = 999999L;

        csvBuilder(membershipNumberStart, membershipNumberEnd);
    }
}
