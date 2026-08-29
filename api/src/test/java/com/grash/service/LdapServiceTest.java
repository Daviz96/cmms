package com.grash.service;

import com.grash.dto.AuthTokens;
import com.grash.dto.LdapLoginRequest;
import com.grash.exception.CustomException;
import com.grash.model.Company;
import com.grash.model.Role;
import com.grash.model.User;
import com.grash.model.enums.RoleCode;
import com.grash.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.test.util.ReflectionTestUtils;

import javax.naming.directory.SearchControls;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MOD-003A — tests for the real LDAP authentication / provisioning / synchronization flow in
 * {@link LdapService}. The LDAP server is mocked through {@link LdapTemplate} and
 * {@link LdapAuthenticationProvider}; no external directory is required.
 *
 * <p>Covers: LDAP disabled, missing org-admin, invalid credentials, JIT provisioning with attribute
 * mapping and company isolation, OU→role mapping, local/LDAP email collision delegation, AD password
 * handling, LDAP-injection escaping, and sync create/disable. Licensing is unchanged and is covered
 * by {@code LicenseServiceTest}.</p>
 */
@ExtendWith(MockitoExtension.class)
class LdapServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RoleService roleService;
    @Mock
    private CompanyService companyService;
    @Mock
    private CacheService cacheService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private LdapAuthenticationProvider ldapAuthenticationProvider;
    @Mock
    private LdapTemplate ldapTemplate;
    @Mock
    private UserService userService;

    @InjectMocks
    private LdapService ldapService;

    private static final String ORG_ADMIN = "atlas-admin@example.local";

    @BeforeEach
    void setUp() {
        // LdapService injects these via field @Autowired; @InjectMocks only does constructor
        // injection here, so wire the non-constructor collaborators explicitly.
        ReflectionTestUtils.setField(ldapService, "ldapAuthenticationProvider", ldapAuthenticationProvider);
        ReflectionTestUtils.setField(ldapService, "ldapTemplate", ldapTemplate);
        ReflectionTestUtils.setField(ldapService, "userService", userService);
        ReflectionTestUtils.setField(ldapService, "ldapOrgAdmin", ORG_ADMIN);
        ReflectionTestUtils.setField(ldapService, "ldapAttrEmail", "mail");
        ReflectionTestUtils.setField(ldapService, "ldapAttrFirstName", "givenName");
        ReflectionTestUtils.setField(ldapService, "ldapAttrLastName", "sn");
        ReflectionTestUtils.setField(ldapService, "usernameAttr", "sAMAccountName");
        ReflectionTestUtils.setField(ldapService, "objectClassAttr", "user");
        ReflectionTestUtils.setField(ldapService, "ldapUserSearchBases", "");
        ReflectionTestUtils.setField(ldapService, "ldapUserSearchFilter", "");
        ReflectionTestUtils.setField(ldapService, "ldapSearchSubtree", true);
        ReflectionTestUtils.setField(ldapService, "ldapOuRoleMappings", "");
        ReflectionTestUtils.setField(ldapService, "ldapSyncEnabled", false);
        ReflectionTestUtils.setField(ldapService, "ldapSyncCreate", false);
        ReflectionTestUtils.setField(ldapService, "ldapSyncUpdate", false);
        ReflectionTestUtils.setField(ldapService, "ldapSyncDisable", false);
    }

    private Role role(RoleCode code) {
        Role r = new Role();
        r.setCode(code);
        return r;
    }

    private AuthTokens tokens() {
        return new AuthTokens("access", "refresh", new Date());
    }

    // ---- Auth guards --------------------------------------------------------

    @Test
    void signinLdap_whenProviderNull_throwsForbidden() {
        ReflectionTestUtils.setField(ldapService, "ldapAuthenticationProvider", null);

        CustomException ex = assertThrows(CustomException.class,
                () -> ldapService.signinLdap(new LdapLoginRequest("jdoe", "secret")));
        assertTrue(ex.getMessage().contains("LDAP authentication is not enabled"));
    }

    @Test
    void signinLdap_whenOrgAdminBlank_throwsServerError() {
        ReflectionTestUtils.setField(ldapService, "ldapOrgAdmin", "");

        assertThrows(CustomException.class,
                () -> ldapService.signinLdap(new LdapLoginRequest("jdoe", "secret")));
    }

    @Test
    void signinLdap_invalidCredentials_throwsForbidden() {
        when(userRepository.findBySsoProviderIdAndSsoProvider("baduser", "LDAP")).thenReturn(Optional.empty());
        when(ldapAuthenticationProvider.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        CustomException ex = assertThrows(CustomException.class,
                () -> ldapService.signinLdap(new LdapLoginRequest("baduser", "wrong")));
        assertTrue(ex.getMessage().contains("LDAP authentication failed"));
    }

    // ---- JIT provisioning + attribute mapping + company isolation + password ----

    @Test
    void signinLdap_newUser_provisionsInOrgAdminCompany_withMappedAttributes_andRandomPassword() {
        Company company = new Company();
        Map<String, String> ldapAttrs = Map.of("email", "jdoe@example.local", "firstName", "John", "lastName", "Doe");

        when(userRepository.findBySsoProviderIdAndSsoProvider("jdoe", "LDAP")).thenReturn(Optional.empty());
        when(ldapTemplate.search(eq(""), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList(ldapAttrs));
        when(companyService.findByOwnerEmailAndOwnsCompany(ORG_ADMIN)).thenReturn(Optional.of(company));
        when(roleService.findDefaultRoles()).thenReturn(List.of(role(RoleCode.LIMITED_TECHNICIAN)));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-random");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenService.createTokenPair(any(User.class))).thenReturn(tokens());

        AuthTokens result = ldapService.signinLdap(new LdapLoginRequest("jdoe", "ad-secret"));

        assertEquals("access", result.getAccessToken());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("LDAP", saved.getSsoProvider());
        assertEquals("jdoe", saved.getSsoProviderId());
        assertEquals("jdoe@example.local", saved.getEmail());   // attribute mapping
        assertEquals("John", saved.getFirstName());
        assertEquals("Doe", saved.getLastName());
        assertTrue(saved.isEnabled());
        assertSame(company, saved.getCompany());                 // company isolation (org-admin company)
        assertEquals(RoleCode.LIMITED_TECHNICIAN, saved.getRole().getCode());

        // Password handling: local password is the hashed random value, never the AD password.
        assertEquals("hashed-random", saved.getPassword());
        assertNotEquals("ad-secret", saved.getPassword());
        verify(passwordEncoder, never()).encode("ad-secret");
    }

    // ---- OU → role mapping --------------------------------------------------

    @Test
    void signinLdap_newUser_mapsOuToRole() {
        ReflectionTestUtils.setField(ldapService, "ldapOuRoleMappings", "admins=ADMIN");
        Company company = new Company();

        when(userRepository.findBySsoProviderIdAndSsoProvider("jdoe", "LDAP")).thenReturn(Optional.empty());
        when(ldapTemplate.search(eq(""), anyString(), any(AttributesMapper.class)))
                .thenReturn(Collections.emptyList());
        when(ldapTemplate.search(eq(""), anyString(), any(ContextMapper.class)))
                .thenReturn(Collections.singletonList("CN=jdoe,OU=Admins,DC=example,DC=local"));
        when(companyService.findByOwnerEmailAndOwnsCompany(ORG_ADMIN)).thenReturn(Optional.of(company));
        when(roleService.findDefaultRoles())
                .thenReturn(List.of(role(RoleCode.ADMIN), role(RoleCode.LIMITED_TECHNICIAN)));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-random");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refreshTokenService.createTokenPair(any(User.class))).thenReturn(tokens());

        ldapService.signinLdap(new LdapLoginRequest("jdoe", "ad-secret"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(RoleCode.ADMIN, userCaptor.getValue().getRole().getCode());
    }

    // ---- Local / LDAP email collision --------------------------------------

    @Test
    void signinLdap_localNonLdapEmail_delegatesToUserServiceSignin() {
        User localUser = new User();
        localUser.setSsoProvider("CLIENT");
        when(userRepository.findByEmailIgnoreCase("jane@example.local")).thenReturn(Optional.of(localUser));
        when(userService.signin("jane@example.local", "pw", "CLIENT")).thenReturn(tokens());

        AuthTokens result = ldapService.signinLdap(new LdapLoginRequest("jane@example.local", "pw"));

        assertEquals("access", result.getAccessToken());
        verify(userService).signin("jane@example.local", "pw", "CLIENT");
        verify(ldapAuthenticationProvider, never()).authenticate(any());
    }

    // ---- LDAP injection -----------------------------------------------------

    @Test
    void extractLdapUserDetails_escapesLdapInjectionInUsername() {
        ArgumentCaptor<String> filterCaptor = ArgumentCaptor.forClass(String.class);
        when(ldapTemplate.search(eq(""), filterCaptor.capture(), any(AttributesMapper.class)))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() ->
                ReflectionTestUtils.invokeMethod(ldapService, "extractLdapUserDetails", "ab*)(uid=*"));

        String filter = filterCaptor.getValue();
        assertFalse(filter.contains("(uid=*"), "raw injection must not appear unescaped: " + filter);
        assertTrue(filter.contains("\\2a"), "wildcard must be escaped: " + filter);
    }

    // ---- Synchronization ----------------------------------------------------

    @Test
    void syncLdapUsers_createsNewUser() {
        ReflectionTestUtils.setField(ldapService, "ldapSyncEnabled", true);
        ReflectionTestUtils.setField(ldapService, "ldapSyncCreate", true);
        Company company = new Company();

        when(companyService.findByOwnerEmailAndOwnsCompany(ORG_ADMIN)).thenReturn(Optional.of(company));
        when(ldapTemplate.search(anyString(), anyString(), any(SearchControls.class), any(AttributesMapper.class)))
                .thenReturn(Collections.singletonList("newuser"));
        when(userRepository.findByCompany_Id(any())).thenReturn(Collections.emptyList());
        when(roleService.findDefaultRoles()).thenReturn(List.of(role(RoleCode.LIMITED_TECHNICIAN)));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-random");

        ldapService.syncLdapUsers();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertEquals("LDAP", saved.getSsoProvider());
        assertEquals("newuser", saved.getSsoProviderId());
        assertSame(company, saved.getCompany());
    }

    @Test
    void syncLdapUsers_disablesUserRemovedFromDirectory() {
        ReflectionTestUtils.setField(ldapService, "ldapSyncEnabled", true);
        ReflectionTestUtils.setField(ldapService, "ldapSyncDisable", true);
        Company company = new Company();

        User oldUser = new User();
        oldUser.setSsoProvider("LDAP");
        oldUser.setSsoProviderId("olduser");
        oldUser.setEmail("olduser@example.local");
        oldUser.setEnabled(true);

        when(companyService.findByOwnerEmailAndOwnsCompany(ORG_ADMIN)).thenReturn(Optional.of(company));
        when(ldapTemplate.search(anyString(), anyString(), any(SearchControls.class), any(AttributesMapper.class)))
                .thenReturn(Collections.emptyList()); // olduser no longer present in the directory
        when(userRepository.findByCompany_Id(any())).thenReturn(List.of(oldUser));

        ldapService.syncLdapUsers();

        assertFalse(oldUser.isEnabled());
        verify(userRepository).save(oldUser);
    }
}
