package com.grash.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grash.controller.LicenseController;
import com.grash.dto.license.LicenseEntitlement;
import com.grash.dto.license.LicensingState;
import com.grash.repository.KeygenRequestTrackerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * MOD-001 — tests for the centralized self-hosted licensing mode.
 *
 * Scenarios (see docs/MOD-001-self-hosted-implementation.md section 15):
 *  - Test 1: self-hosted enabled -> valid state, all entitlements, no Keygen call.
 *  - Test 2: self-hosted disabled + no license -> commercial behavior unchanged (regression).
 *  - Test 3: /license/state (LicenseController) returns the self-hosted state.
 *  - Test 4: entitlement policy is explicit (exactly the full enum).
 *  - Test 6: Keygen is not contacted in self-hosted mode, even with a license key present.
 */
@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private KeygenRequestTrackerRepository keygenRequestTrackerRepository;

    private LicenseService newLicenseService(boolean selfHosted, String licenseKey) {
        LicenseService service = new LicenseService(objectMapper, keygenRequestTrackerRepository);
        ReflectionTestUtils.setField(service, "selfHostedMode", selfHosted);
        ReflectionTestUtils.setField(service, "licenseKey", licenseKey);
        ReflectionTestUtils.setField(service, "licenseFingerprintRequired", false);
        ReflectionTestUtils.setField(service, "keygenAccountId", "test-account");
        ReflectionTestUtils.setField(service, "licenseFilePath", null);
        return service;
    }

    private static Set<String> allEntitlementNames() {
        return Arrays.stream(LicenseEntitlement.values()).map(Enum::name).collect(Collectors.toSet());
    }

    // ---- Test 1 ------------------------------------------------------------

    @Test
    void selfHosted_enabled_returnsValidStateWithAllEntitlements_andNoKeygenCall() {
        LicenseService service = newLicenseService(true, null); // no LICENSE_KEY, no LICENSE_FILE_PATH

        LicensingState state = service.getLicensingState();

        assertTrue(state.isValid(), "self-hosted state must be valid");
        assertTrue(state.isHasLicense(), "self-hosted state must report hasLicense=true");
        assertEquals("Self-Hosted", state.getPlanName());
        assertNull(state.getExpirationDate(), "self-hosted license must not expire");
        assertEquals(allEntitlementNames(), state.getEntitlements());

        for (LicenseEntitlement entitlement : LicenseEntitlement.values()) {
            assertTrue(service.hasEntitlement(entitlement), "expected entitlement granted: " + entitlement);
        }
        assertTrue(service.isSSOEnabled());

        // Keygen validation would go through the request tracker; it must never be touched here.
        verifyNoInteractions(keygenRequestTrackerRepository);
    }

    // ---- Test 2 (regression) ----------------------------------------------

    @Test
    void selfHosted_disabled_withoutLicense_returnsInvalidState_regression() {
        LicenseService service = newLicenseService(false, null);

        LicensingState state = service.getLicensingState();

        assertFalse(state.isValid(), "without a license the commercial state must be invalid");
        assertFalse(state.isHasLicense());
        assertTrue(state.getEntitlements().isEmpty());

        for (LicenseEntitlement entitlement : LicenseEntitlement.values()) {
            assertFalse(service.hasEntitlement(entitlement), "entitlement must be denied without license: " + entitlement);
        }
        assertFalse(service.isSSOEnabled());
    }

    // ---- Test 3 (/license/state endpoint) ---------------------------------

    @Test
    void licenseStateEndpoint_returnsSelfHostedState() {
        LicenseService service = newLicenseService(true, null);
        LicenseController controller = new LicenseController(service);

        LicensingState state = controller.getValidity(null);

        assertTrue(state.isValid());
        assertEquals("Self-Hosted", state.getPlanName());
        assertEquals(allEntitlementNames(), state.getEntitlements());
    }

    // ---- Test 4 (explicit entitlement policy) -----------------------------

    @Test
    void selfHosted_entitlementPolicy_isExplicitFullEnum() {
        LicenseService service = newLicenseService(true, null);

        LicensingState state = service.getLicensingState();

        assertEquals(allEntitlementNames(), state.getEntitlements(),
                "self-hosted policy must grant exactly the full LicenseEntitlement enum, no more and no less");
    }

    // ---- Test 6 (Keygen isolation) ----------------------------------------

    @Test
    void selfHosted_enabled_doesNotContactKeygen_evenWhenLicenseKeyPresent() {
        LicenseService service = newLicenseService(true, "some-commercial-license-key");

        LicensingState state = service.getLicensingState();

        assertTrue(state.isValid());
        assertEquals("Self-Hosted", state.getPlanName());
        // A commercial validation with a key present would hit the Keygen request tracker;
        // self-hosted mode must short-circuit before that path.
        verifyNoInteractions(keygenRequestTrackerRepository);
    }
}
