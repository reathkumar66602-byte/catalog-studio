package com.catalogstudio.shoot.service;

public final class ShootPrompt {

    private ShootPrompt() {}

    public static String forKind(String kind, String modelAge) {
        return forKind(kind, modelAge, false);
    }

    public static String forKind(String kind, String modelAge, boolean whiteBackground) {
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
        return base(modelAge) + " " + background(whiteBackground) + " " + angle;
    }

    private static String background(boolean whiteBackground) {
        if (whiteBackground) {
            return "The entire background behind the model and garment must be pure white, RGB 255 255 255. "
                    + "No cream, beige, grey, pastel, floor, wall, room, or coloured backdrop. "
                    + "No background shadow. This set is for Flipkart and Amazon.";
        }
        return "Place the model in a beautiful styled Meesho scene, not a plain studio. "
                + "Use a real setting such as a sunlit Indian home with a sofa and plants, a courtyard, or a festive room with curtains, furniture, and colour. "
                + "The backdrop must show depth and decor. Do not use a seamless paper sweep, a plain beige wall, a plain cream wall, or an empty studio. "
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
            return "The model is an Indian child in the " + range
                    + " age range, photographed as a modest children's clothing catalog. Everyday kidswear only.";
        }
        if (ShootOptions.teenAge(modelAge)) {
            return "The model is an Indian teenager in the " + range
                    + " age range, photographed as a modest teen clothing catalog.";
        }
        if ("50+".equals(modelAge)) {
            return "The model is an older Indian adult, 50 years or older.";
        }
        return "The model is an Indian adult in the " + range + " age range.";
    }
}
