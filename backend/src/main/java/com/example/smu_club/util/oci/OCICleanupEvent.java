package com.example.smu_club.util.oci;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class OCICleanupEvent {

    private final List<String> fileKeysToDelete;


}
