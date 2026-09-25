package com.catalogstudio.shoot.service;

import com.catalogstudio.common.exception.ApiException;
import com.catalogstudio.shoot.dto.ShootResponse;
import com.catalogstudio.shoot.dto.ShootResponse.ShootImageView;
import com.catalogstudio.shoot.entity.ProductShoot;
import com.catalogstudio.shoot.entity.ProductShootImage;
import com.catalogstudio.shoot.repository.ProductShootImageRepository;
import com.catalogstudio.shoot.repository.ProductShootRepository;
import com.catalogstudio.storage.StorageService;
import com.catalogstudio.subscription.service.SubscriptionAccessService;
import com.catalogstudio.user.entity.User;
import com.catalogstudio.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ShootService {

    private final UserRepository userRepository;
    private final ProductShootRepository shootRepository;
    private final ProductShootImageRepository imageRepository;
    private final StorageService storageService;
    private final CatalogImageEditor imageEditor;
    private final SubscriptionAccessService accessService;

    public ShootResponse create(
            Long userId,
            String modeRaw,
            String modelAgeRaw,
            List<String> anglesRaw,
            boolean flipkart,
            boolean meesho,
            MultipartFile front,
            MultipartFile back,
            List<MultipartFile> products
    ) {
        return create(userId, modeRaw, modelAgeRaw, anglesRaw, flipkart, meesho,
                ShootOptions.DEFAULT_MEESHO_BACKGROUND, front, back, products);
    }

    public ShootResponse create(
            Long userId,
            String modeRaw,
            String modelAgeRaw,
            List<String> anglesRaw,
            boolean flipkart,
            boolean meesho,
            String meeshoBackgroundRaw,
            MultipartFile front,
            MultipartFile back,
            List<MultipartFile> products
    ) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        String mode = ShootOptions.mode(modeRaw);
        String modelAge = ShootOptions.modelAge(modelAgeRaw);
        List<String> angles = ShootOptions.angles(anglesRaw, false);
        String meeshoBackground = flipkart
                ? ShootOptions.DEFAULT_MEESHO_BACKGROUND
                : ShootOptions.meeshoBackground(meeshoBackgroundRaw);
        boolean trial = accessService.statusOf(user).trialActive();

        List<StorageService.StoredFile> productFiles = new ArrayList<>();
        StorageService.StoredFile backFile = null;
        if ("COMBO".equals(mode)) {
            List<MultipartFile> uploads = products == null ? List.of() : products.stream().filter(file -> file != null && !file.isEmpty()).toList();
            if (uploads.isEmpty()) {
                throw ApiException.badRequest("Upload at least one product photo");
            }
            if (uploads.size() > 6) {
                throw ApiException.badRequest("A combo shoot accepts up to 6 product photos");
            }
            if (angles.contains("BACK")) {
                throw ApiException.badRequest("Back angle is available on a single product when you upload a back photo");
            }
            for (MultipartFile file : uploads) {
                productFiles.add(storageService.store(file));
            }
        } else {
            if (front == null || front.isEmpty()) {
                throw ApiException.badRequest("Front photo is required");
            }
            productFiles.add(storageService.store(front));
            if (back != null && !back.isEmpty()) {
                backFile = storageService.store(back);
            } else if (angles.contains("BACK")) {
                throw ApiException.badRequest("Upload a back photo to unlock the back angle");
            }
        }

        List<ShootPlan.Ref> refs = new ArrayList<>();
        for (int i = 0; i < productFiles.size(); i++) {
            StorageService.StoredFile stored = productFiles.get(i);
            refs.add(new ShootPlan.Ref(filename(i + 1, stored.contentType()), stored.contentType(), stored.bytes()));
        }
        ShootPlan.Ref backRef = backFile == null
                ? null
                : new ShootPlan.Ref(filename(0, backFile.contentType()), backFile.contentType(), backFile.bytes());

        List<ShootPlan.Job> full = ShootPlan.jobs(mode, modelAge, angles, flipkart, false, refs, backRef, meeshoBackground);
        List<ShootPlan.Job> jobs = trial && full.size() > 1 ? List.of(full.get(0)) : full;
        if (jobs.isEmpty()) {
            throw ApiException.badRequest("Choose at least one photo to generate");
        }

        ProductShoot shoot = shootRepository.save(ProductShoot.builder()
                .user(user)
                .mode(mode)
                .modelAge(modelAge)
                .marketplace(flipkart || meesho)
                .trialLimited(trial && full.size() > 1)
                .status("PENDING")
                .build());
        int sort = 0;
        for (StorageService.StoredFile stored : productFiles) {
            saveImage(shoot, "SOURCE", false, stored, sort++);
        }
        if (backFile != null) {
            saveImage(shoot, "SOURCE_BACK", false, backFile, sort);
        }

        List<Generated> generated = generate(jobs);
        List<String> failures = generated.stream().filter(item -> item.error() != null).map(Generated::error).toList();
        List<Generated> ready = generated.stream().filter(item -> item.bytes() != null).toList();
        if (ready.isEmpty()) {
            shoot.setStatus("FAILED");
            shoot.setErrorMessage(String.join(" ", failures));
            shootRepository.save(shoot);
            throw new ApiException(HttpStatus.BAD_GATEWAY, shoot.getErrorMessage());
        }
        int outputOrder = 100;
        for (Generated item : ready) {
            StorageService.StoredFile stored = storageService.storeBytes(item.bytes(), "image/png");
            saveImage(shoot, item.kind(), true, stored, outputOrder++);
        }
        shoot.setStatus(failures.isEmpty() ? "COMPLETED" : "PARTIAL");
        shoot.setErrorMessage(failures.isEmpty() ? null : String.join(" ", failures));
        shootRepository.save(shoot);
        return toResponse(shootRepository.findByUuidAndUser_Id(shoot.getUuid(), userId).orElse(shoot));
    }

    @Transactional(readOnly = true)
    public List<ShootResponse> recent(Long userId) {
        return shootRepository.findTop12ByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShootResponse get(Long userId, UUID id) {
        return toResponse(load(userId, id));
    }

    @Transactional(readOnly = true)
    public ProductShootImage downloadable(Long userId, UUID shootId, UUID imageId) {
        ProductShoot shoot = load(userId, shootId);
        ProductShootImage image = imageRepository.findByUuidAndShoot_User_Id(imageId, userId)
                .orElseThrow(() -> ApiException.notFound("Image not found"));
        if (!image.getShoot().getId().equals(shoot.getId()) || !image.isGenerated()) {
            throw ApiException.notFound("Image not found");
        }
        return image;
    }

    private ProductShoot load(Long userId, UUID id) {
        return shootRepository.findByUuidAndUser_Id(id, userId)
                .orElseThrow(() -> ApiException.notFound("Shoot not found"));
    }

    private void saveImage(ProductShoot shoot, String kind, boolean generated, StorageService.StoredFile stored, int sortOrder) {
        imageRepository.save(ProductShootImage.builder()
                .shoot(shoot)
                .kind(kind)
                .generated(generated)
                .storageKey(stored.storageKey())
                .publicUrl(stored.publicUrl())
                .contentType(stored.contentType())
                .sortOrder(sortOrder)
                .build());
    }

    private List<Generated> generate(List<ShootPlan.Job> jobs) {
        List<Generated> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Generated>> futures = new ArrayList<>();
            for (ShootPlan.Job job : jobs) {
                futures.add(executor.submit(() -> run(job)));
            }
            for (int i = 0; i < futures.size(); i++) {
                ShootPlan.Job job = jobs.get(i);
                try {
                    results.add(futures.get(i).get());
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    String message = cause.getMessage() == null ? "OpenAI image generation failed" : cause.getMessage();
                    results.add(new Generated(job.kind(), null, label(job.kind()) + ": " + message));
                }
            }
        }
        return results;
    }

    private Generated run(ShootPlan.Job job) {
        try {
            byte[] bytes = imageEditor.edit(job.references(), job.prompt(), job.size());
            return new Generated(job.kind(), bytes, null);
        } catch (RuntimeException ex) {
            String message = ex.getMessage() == null ? "OpenAI image generation failed" : ex.getMessage();
            return new Generated(job.kind(), null, label(job.kind()) + ": " + message);
        }
    }

    private ShootResponse toResponse(ProductShoot shoot) {
        List<ShootImageView> images = shoot.getImages() == null ? List.of() : shoot.getImages().stream()
                .filter(ProductShootImage::isGenerated)
                .sorted(Comparator.comparingInt(ProductShootImage::getSortOrder))
                .map(image -> new ShootImageView(
                        image.getUuid(),
                        image.getKind(),
                        image.getPublicUrl(),
                        true,
                        image.getSortOrder()))
                .toList();
        return new ShootResponse(
                shoot.getUuid(),
                shoot.getMode(),
                shoot.getModelAge(),
                shoot.isMarketplace(),
                shoot.isTrialLimited(),
                shoot.getStatus(),
                shoot.getErrorMessage(),
                images,
                shoot.getCreatedAt());
    }

    private static String filename(int index, String contentType) {
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String ext = type.contains("png") ? ".png" : type.contains("webp") ? ".webp" : ".jpg";
        return index == 0 ? "back" + ext : "garment-" + index + ext;
    }

    public static String label(String kind) {
        return switch (kind) {
            case "FRONT" -> "Front";
            case "BACK" -> "Back";
            case "SIDE" -> "Side";
            case "SHOP" -> "Shop shot";
            case "MARKETPLACE" -> "Flipkart / Amazon image";
            case "MEESHO" -> "Meesho image";
            default -> "Photo";
        };
    }

    private record Generated(String kind, byte[] bytes, String error) {}
}
