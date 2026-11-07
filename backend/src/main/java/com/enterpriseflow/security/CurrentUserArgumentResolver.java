package com.enterpriseflow.security;

import com.enterpriseflow.user.RoleName;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Lets controllers declare a {@link CurrentUser} parameter instead of digging into the SecurityContext. */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CurrentUser.class.equals(parameter.getParameterType());
    }

    @Override
    public CurrentUser resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                       NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AuthenticationCredentialsNotFoundException("No authenticated user");
        }
        return fromJwt(jwt);
    }

    static CurrentUser fromJwt(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtTokenService.ROLES_CLAIM);
        Set<RoleName> roleNames = roles == null ? Set.of() : roles.stream()
                .map(RoleName::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        return new CurrentUser(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"), roleNames);
    }
}
