package com.grash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.dto.license.LicenseEntitlement;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.repository.CheckListRepository;
import com.grash.repository.KeygenRequestTrackerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MOD-002 — verifies that the usage-based commercial limits are disabled in self-hosted mode
 * through the {@code UNLIMITED_*} entitlements granted by MOD-001, WITHOUT any change to the
 * limit services or to {@code Consts.usageBasedFreeLimits}.
 *
 * <p>Two independent links are proven:</p>
 * <ol>
 *   <li>Licensing side (real {@link LicenseService}): every {@code UNLIMITED_*} entitlement is
 *       granted in self-hosted mode and denied in commercial mode without a license.</li>
 *   <li>Limit-applying code (real {@link ChecklistService#checkUsageBasedLimit}, representative of
 *       the identical pattern in Asset/User/Location/Part/PM/WorkOrder/Meter services): with the
 *       entitlement granted the count check is short-circuited and the limit is not enforced, even
 *       when the count already exceeds the free limit.</li>
 * </ol>
 *
 * <p>Note on names: the MOD-002 brief lists {@code UNLIMITED_PREVENTIVE_MAINTENANCE} and
 * {@code UNLIMITED_WORK_ORDERS}, but the actual enum uses {@code UNLIMITED_PM_SCHEDULES} and
 * {@code UNLIMITED_ACTIVE_WORK_ORDERS}. The real names are used here.</p>
 */
@ExtendWith(MockitoExtension.class)
class SelfHostedUsageLimitsTest {

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private KeygenRequestTrackerRepository keygenRequestTrackerRepository;
    @Mock
    private CheckListRepository checklistRepository;
    @Mock
    private LicenseService licenseServiceMock;

    private LicenseService realLicenseService(boolean selfHosted) {
        LicenseService service = new LicenseService(objectMapper, keygenRequestTrackerRepository);
        ReflectionTestUtils.setField(service, "selfHostedMode", selfHosted);
        ReflectionTestUtils.setField(service, "licenseKey", null);
        ReflectionTestUtils.setField(service, "licenseFingerprintRequired", false);
        ReflectionTestUtils.setField(service, "keygenAccountId", "test-account");
        ReflectionTestUtils.setField(service, "licenseFilePath", null);
        return service;
    }

    // ---- Link 1: licensing side (real LicenseService), all 8 usage-based entitlements ----------

    @ParameterizedTest
    @EnumSource(value = LicenseEntitlement.class, names = {
            "UNLIMITED_ASSETS", "UNLIMITED_USERS", "UNLIMITED_LOCATIONS", "UNLIMITED_PARTS",
            "UNLIMITED_PM_SCHEDULES", "UNLIMITED_ACTIVE_WORK_ORDERS", "UNLIMITED_CHECKLISTS", "UNLIMITED_METERS"
    })
    void selfHosted_grantsEveryUnlimitedEntitlement(LicenseEntitlement entitlement) {
        assertTrue(realLicenseService(true).hasEntitlement(entitlement),
                "self-hosted mode must grant " + entitlement);
    }

    @ParameterizedTest
    @EnumSource(value = LicenseEntitlement.class, names = {
            "UNLIMITED_ASSETS", "UNLIMITED_USERS", "UNLIMITED_LOCATIONS", "UNLIMITED_PARTS",
            "UNLIMITED_PM_SCHEDULES", "UNLIMITED_ACTIVE_WORK_ORDERS", "UNLIMITED_CHECKLISTS", "UNLIMITED_METERS"
    })
    void commercialWithoutLicense_deniesEveryUnlimitedEntitlement(LicenseEntitlement entitlement) {
        assertFalse(realLicenseService(false).hasEntitlement(entitlement),
                "commercial mode without a license must deny " + entitlement);
    }

    // ---- Link 2: real limit-applying code (ChecklistService.checkUsageBasedLimit) -------------

    @Test
    void commercialMode_enforcesChecklistFreeLimit_whenExceeded() {
        ChecklistService service = new ChecklistService(checklistRepository, null, null, null, licenseServiceMock);
        when(licenseServiceMock.hasEntitlement(LicenseEntitlement.UNLIMITED_CHECKLISTS)).thenReturn(false);
        when(checklistRepository.hasMoreThan(any(), any())).thenReturn(true); // count already over the free limit

        assertThrows(CustomException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "checkUsageBasedLimit", new Company()));
    }

    @Test
    void selfHostedMode_bypassesChecklistLimit_evenWhenExceeded() {
        ChecklistService service = new ChecklistService(checklistRepository, null, null, null, licenseServiceMock);
        when(licenseServiceMock.hasEntitlement(LicenseEntitlement.UNLIMITED_CHECKLISTS)).thenReturn(true);

        assertDoesNotThrow(
                () -> ReflectionTestUtils.invokeMethod(service, "checkUsageBasedLimit", new Company()));

        // Short-circuit: the count query is never reached once the entitlement is granted.
        verify(checklistRepository, never()).hasMoreThan(any(), any());
    }
}
