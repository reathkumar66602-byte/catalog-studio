package com.catalogstudio.shoot.service;

import java.util.ArrayList;
import java.util.List;

public final class ShootPlan {

    private ShootPlan() {}

    public record Ref(String filename, String contentType, byte[] bytes) {}

    public static final String AUTO = "auto";
    public static final String SQUARE = "1024x1024";

    public record Job(String kind, String prompt, String size, List<Ref> references) {}

    public static List<Job> jobs(
            String mode,
            String modelAge,
            List<String> angles,
            boolean flipkart,
            boolean trial,
            List<Ref> products,
            Ref back
    ) {
        return jobs(mode, modelAge, angles, flipkart, trial, products, back, ShootOptions.DEFAULT_MEESHO_BACKGROUND);
    }

    public static List<Job> jobs(
            String mode,
            String modelAge,
            List<String> angles,
            boolean flipkart,
            boolean trial,
            List<Ref> products,
            Ref back,
            String meeshoBackground
    ) {
        String background = flipkart ? ShootOptions.DEFAULT_MEESHO_BACKGROUND : ShootOptions.meeshoBackground(meeshoBackground);
        List<Ref> catalogRefs = new ArrayList<>(products);
        if (back != null) {
            catalogRefs.add(back);
        }
        List<Job> jobs = new ArrayList<>();
        for (String angle : angles) {
            List<Ref> refs = "BACK".equals(angle) && back != null ? List.of(products.get(0), back) : List.copyOf(catalogRefs);
            jobs.add(new Job(angle, ShootPrompt.forKind(angle, modelAge, flipkart, background), AUTO, refs));
        }
        if (trial && jobs.size() > 1) {
            return List.of(jobs.get(0));
        }
        return List.copyOf(jobs);
    }

}
