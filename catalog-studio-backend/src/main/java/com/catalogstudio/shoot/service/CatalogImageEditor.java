package com.catalogstudio.shoot.service;

import java.util.List;

public interface CatalogImageEditor {

    byte[] edit(List<ShootPlan.Ref> images, String prompt, String size);
}
