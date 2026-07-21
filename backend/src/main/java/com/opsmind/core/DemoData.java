package com.opsmind.core;

import com.opsmind.domain.Types.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DemoData implements CommandLineRunner {
    private final UserRepository users;private final MonitoredServiceRepository services;private final AlertRuleRepository rules;private final PasswordEncoder passwords;private final boolean enabled;
    DemoData(UserRepository users,MonitoredServiceRepository services,AlertRuleRepository rules,PasswordEncoder passwords,@Value("${opsmind.seed-demo-data}")boolean enabled){this.users=users;this.services=services;this.rules=rules;this.passwords=passwords;this.enabled=enabled;}
    @Override @Transactional public void run(String...args){if(!enabled||users.count()>0)return;users.save(new User("admin@opsmind.local",passwords.encode("Admin123!"),"OpsMind Admin",Role.ADMIN));users.save(new User("engineer@opsmind.local",passwords.encode("Engineer123!"),"Demo Engineer",Role.ENGINEER));String key="opm_demo_key";MonitoredService s=services.save(new MonitoredService("payment-api","production","Payments",Hashing.sha256(key)));rules.save(new AlertRule(s.id,"Database timeout detected",RuleType.KEYWORD,"timeout",1,300,Severity.SEV2,30));}
}
