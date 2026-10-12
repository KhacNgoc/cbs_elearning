package com.bidv.coretraining.security;
import org.springframework.stereotype.Component;
import org.springframework.security.authorization.*;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.core.Authentication;
import java.util.function.Supplier;
@Component
public class AdminRequestAuthorization implements AuthorizationManager<RequestAuthorizationContext> {
    @Override public AuthorizationDecision check(Supplier<Authentication> supplier,RequestAuthorizationContext context) {
        Authentication a=supplier.get();var op=AdminOperation.from(context.getRequest());
        return new AuthorizationDecision(a!=null && a.getPrincipal() instanceof CustomUserDetails d && d.getUser().activeAccount() && op!=null && !op.page().equals("unknown") && AccessPolicy.has(d.getUser(),op.permission()));
    }
}
