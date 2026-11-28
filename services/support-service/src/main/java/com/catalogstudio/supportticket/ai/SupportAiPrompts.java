package com.catalogstudio.supportticket.ai;

final class SupportAiPrompts {

    private SupportAiPrompts() {}

    static String system(String locale) {
        return """
                You are the CatalogStudio customer support assistant.
                CatalogStudio helps Indian sellers manage product catalogs, marketplace listings \
                (Meesho, Amazon, Flipkart), OMS orders, wholesale supplier/retailer flows, \
                inventory, subscriptions/billing, product shoot/AI photos, and label/crop tools.

                Rules:
                - Respond in the user's locale: %s
                - Only describe real CatalogStudio capabilities from the knowledge context
                - Never invent features, quotas, refunds, or claim you completed an action
                - If unsure, say so and suggest creating a support ticket
                - Never ask for passwords, API secrets, or OTP codes
                - Keep answers concise and practical
                """.formatted(locale == null || locale.isBlank() ? "en" : locale);
    }

    static String escalateMessage(String locale) {
        String code = locale == null ? "en" : locale.toLowerCase();
        return switch (code) {
            case "hi" -> "मुझे इस सवाल का भरोसेमंद जवाब नहीं मिला। कृपया सपोर्ट टिकट बनाएँ — हमारी टीम मदद करेगी।";
            case "bn" -> "এই প্রশ্নের নির্ভরযোগ্য উত্তর দিতে পারছি না। অনুগ্রহ করে একটি সাপোর্ট টিকিট তৈরি করুন।";
            case "mr" -> "या प्रश्नाचे खात्रीशीर उत्तर देता येत नाही. कृपया सपोर्ट तिकीट तयार करा.";
            case "ta" -> "இந்த கேள்விக்கு உறுதியான பதில் இல்லை. தயவுசெய்து ஒரு சப்போர்ட் டிக்கெட் உருவாக்கவும்.";
            case "kn" -> "ಈ ಪ್ರಶ್ನೆಗೆ ವಿಶ್ವಾಸಾರ್ಹ ಉತ್ತರ ಸಿಗಲಿಲ್ಲ. ದಯವಿಟ್ಟು ಸಪೋರ್ಟ್ ಟಿಕೆಟ್ ರಚಿಸಿ.";
            case "pa" -> "ਇਸ ਸਵਾਲ ਦਾ ਭਰੋਸੇਯੋਗ ਜਵਾਬ ਨਹੀਂ ਮਿਲਿਆ। ਕਿਰਪਾ ਕਰਕੇ ਸਪੋਰਟ ਟਿਕਟ ਬਣਾਓ।";
            case "ur" -> "مجھے اس سوال کا قابلِ اعتماد جواب نہیں ملا۔ براہِ کرم سپورٹ ٹکٹ بنائیں۔";
            case "ja" -> "この内容には確実なお答えができません。サポートチケットを作成してください。";
            case "zh" -> "我无法可靠地回答这个问题。请创建支持工单，我们的团队会协助您。";
            case "gu" -> "આ પ્રશ્નનો વિશ્વસનીય જવાબ મળ્યો નથી. કૃપા કરીને સપોર્ટ ટિકિટ બનાવો.";
            case "or" -> "ଏହି ପ୍ରଶ୍ନର ବିଶ୍ୱସନୀୟ ଉତ୍ତର ମିଳିଲା ନାହିଁ। ଦୟାକରି ଏକ ସପୋର୍ଟ ଟିକେଟ୍ ତିଆରି କରନ୍ତୁ।";
            default -> "I could not confidently answer that from CatalogStudio help articles. "
                    + "Please create a support ticket and our team will help.";
        };
    }
}
