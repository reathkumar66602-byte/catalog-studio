package com.catalogstudio.shoot.service;

public final class ShootPrompt {

    private ShootPrompt() {}

    public static String forKind(String kind, String modelAge) {
        return forKind(kind, modelAge, false, ShootOptions.DEFAULT_MEESHO_BACKGROUND);
    }

    public static String forKind(String kind, String modelAge, boolean whiteBackground) {
        return forKind(kind, modelAge, whiteBackground, ShootOptions.DEFAULT_MEESHO_BACKGROUND);
    }

    public static String forKind(String kind, String modelAge, boolean whiteBackground, String meeshoBackground) {
        String angle = switch (kind) {
            case "FRONT" -> "Front view of the same product. If a model is wearing it, the model faces the camera.";
            case "BACK" -> "Back view. If a model is wearing it, the model is turned away. Follow the back reference photo for the garment back.";
            case "SIDE" -> "Three-quarter side view of the same product.";
            case "SHOP" -> whiteBackground
                    ? "Full catalog view of the same product. Do not zoom in."
                    : "Lifestyle photograph in a bright styled setting. Do not zoom in.";
            case "MARKETPLACE", "MEESHO" -> "Catalog view of the same product.";
            default -> throw new IllegalArgumentException("Unknown shoot kind: " + kind);
        };
        return base(modelAge) + " " + background(whiteBackground, meeshoBackground) + " " + angle;
    }

    private static String background(boolean whiteBackground, String meeshoBackground) {
        if (whiteBackground) {
            return "The entire background behind the model and garment must be pure white, RGB 255 255 255. "
                    + "No cream, beige, grey, pastel, floor, wall, room, or coloured backdrop. "
                    + "No background shadow. This set is for Flipkart and Amazon.";
        }
        return meeshoScene(meeshoBackground);
    }

    private static String meeshoScene(String meeshoBackground) {
        String choice = ShootOptions.meeshoBackground(meeshoBackground);
        if ("AUTO".equals(choice)) {
            return "Place the model in a beautiful styled Meesho scene, not a plain studio. "
                    + "Study the garment colour, print, fabric, occasion, and style in the reference photo, "
                    + "then choose one beautiful Indian lifestyle background that flatters this exact outfit. "
                    + "Examples: festive home with marigolds and brass lamps for ethnic or festive wear; "
                    + "sunlit living room with sofa and plants for everyday wear; "
                    + "bright courtyard or veranda for light summer looks. "
                    + "Pick the single best match for this cloth — do not default to a plain beige wall. "
                    + "The backdrop must show depth and decor. Do not use a seamless paper sweep, a plain beige wall, "
                    + "a plain cream wall, or an empty studio. "
                    + "The background must not be plain white or pure white. This set is for Meesho.";
        }
        String scene = switch (choice) {
            case "FESTIVE_HOME" -> "a warm festive Indian home interior: hanging yellow and orange marigold garlands, "
                    + "soft warm fairy lights, a traditional brass oil lamp or diya stand, "
                    + "and a wooden console with festive decor under soft golden indoor light with gentle depth of field";
            case "LIVING_ROOM" -> "a sunlit modern Indian living room with a cream sofa, tall green plants in woven baskets, "
                    + "floor-length beige curtains, light flooring, and soft diffused daylight";
            case "COURTYARD" -> "a bright Indian home courtyard or veranda with plants, warm natural daylight, "
                    + "soft shadows, and lifestyle depth";
            default -> "a beautiful styled Indian home interior with furniture, plants, curtains, and colour";
        };
        return "Place the model in a beautiful styled Meesho scene, not a plain studio. "
                + "Use this exact setting: " + scene + ". "
                + "The backdrop must show depth and decor. Do not use a seamless paper sweep, a plain beige wall, "
                + "a plain cream wall, or an empty studio. "
                + "The background must not be plain white or pure white. This set is for Meesho.";
    }

    private static String base(String modelAge) {
        return "Commercial fashion catalog photograph for an online clothing store. "
                + "Recreate the exact garment from the reference photo: same colour, print, embroidery, fabric, pattern, and design. "
                + "Do not redesign the product. "
                + ageLine(modelAge) + " "
                + "The model must be Indian: an Indian woman, man, girl, boy, or older adult who matches the garment. "
                + "Do not use a European, East Asian, African, or any non-Indian model. "
                + "Modest everyday catalog styling, fully clothed, natural standing pose, clean light, no text, no watermark, "
                + "and no extra accessories that hide the product. "
                + framing();
    }

    private static String framing() {
        return "Match the frame to the reference photo, whatever product it shows. "
                + "A full outfit on a person must include the head, hands, and hem. "
                + "A single piece such as a top, bottom, saree, lehenga, dupatta, kurta, dress, footwear, bag, or jewellery must show that entire piece. "
                + "A flat lay, hanging garment, or close product photo must keep that same kind of shot and include every edge. "
                + "When several products are in the reference, keep every product fully visible. "
                + "Never zoom in. Leave clear space on all four sides. Do not crop or cut off any part of the product.";
    }

    private static String ageLine(String modelAge) {
        String range = "50+".equals(modelAge) ? "50 years or older" : modelAge + " years";
        if (ShootOptions.childAge(modelAge)) {
            return "The model is a cute Indian child in the " + range
                    + " age range, photographed as a modest children's clothing catalog. "
                    + "Warm cheerful expression, soft friendly features, everyday kidswear only.";
        }
        if (ShootOptions.teenAge(modelAge)) {
            return "The model is a cute Indian teenager in the " + range
                    + " age range, photographed as a modest teen clothing catalog. "
                    + "Natural, camera-friendly expression.";
        }
        if ("50+".equals(modelAge)) {
            return "The model is an older Indian adult, 50 years or older, with a warm pleasant expression.";
        }
        return "The model is a cute Indian adult in the " + range
                + " age range, with a pleasant camera-friendly expression.";
    }
}
