package com.tuicr.scaffold.server;


import com.tuicr.scaffold.server.properties.Secure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.access.AccessDecisionVoter;
import org.springframework.security.access.vote.AffirmativeBased;
import org.springframework.security.access.vote.AuthenticatedVoter;
import org.springframework.security.access.vote.RoleVoter;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.encoding.Md5PasswordEncoder;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;
import org.springframework.security.web.access.expression.DefaultWebSecurityExpressionHandler;
import org.springframework.security.web.access.expression.WebExpressionVoter;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.List;

/**
 * spring security安全认证集成
 * @author ylxia
 * @version 1.0
 * @package com.tuicr.weibo.server.config
 * @date 15/12/17
 */

@Slf4j
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({
        Secure.class,
})
@Order(SecurityProperties.ACCESS_OVERRIDE_ORDER)
@EnableGlobalMethodSecurity(securedEnabled = true)
public class SecurityConfig extends WebSecurityConfigurerAdapter {



    public SecurityConfig(){

        //自定义loginfrom提交 请注意不要随便改为true,
        // isCustomLoginPage在初始化的时候为false,进入默认formlogin导致configure中设置的相关错误跳转失效
        super(false);
    }



    @Value("${keystore.password}")
    private String keyStorePwd;

    @Value("${keystore.alias}")
    private String keyStoreAlias;

    @Autowired
    private Secure secure;

    @Override
    public void configure(WebSecurity web) throws Exception {
        web
                .ignoring()
                .antMatchers(MvcConfig.IGNORE_RESOURCES);
    }


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                    .antMatchers(MvcConfig.IGNORE_URIS)
                .permitAll()
                    .anyRequest().authenticated()
                .and()
                //  .addFilter(captchaFilter())
                    .addFilterBefore(captchaFilter(),UsernamePasswordAuthenticationFilter.class)
                    .csrf()
                .and()
                    .formLogin()
                        .usernameParameter(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_USERNAME_KEY)
                        .passwordParameter(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY)
                        .loginPage(secure.getLoginPage())
                        .loginProcessingUrl(secure.getLoginProcessingUrl())
                        .defaultSuccessUrl(secure.getLoginSuccessUrl())
                        .failureUrl(secure.getBadCredentialsUrl()).permitAll()
                .and()
                    .logout()
                        .logoutUrl(secure.getLogoutUrl())
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessUrl(secure.getLoginPage())
                .and()
                    .rememberMe()
                .and()
                    .exceptionHandling().accessDeniedPage(secure.getAccessDeniedUrl())
                .and()
                    .sessionManagement()
                    .maximumSessions(1)
                    .maxSessionsPreventsLogin(true)
                    .expiredUrl(secure.getExpireUrl())
                .and()
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                    .invalidSessionUrl(secure.getLoginPage());
    }



    /**
     * 自定义验证码
     *
     * @return
     * @throws Exception
     */
    public Filter captchaFilter() throws Exception {
        CaptchaAuthenticationProcessingFilter captchaAuthenticationProcessingFilter =  new CaptchaAuthenticationProcessingFilter(false);
        return captchaAuthenticationProcessingFilter;
    }

    @Autowired
    public void registerAuthentication(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication()
                .withUser("admin")
                .password("admin!#123")
                .roles("ADMIN");
    }

//
//    @Autowired
//    public void registerAuthentication(AuthenticationManagerBuilder auth) throws Exception {
//        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
//        daoAuthenticationProvider.setUserDetailsService(authUserHandler());
//        daoAuthenticationProvider.setSaltSource(user -> user.getUsername());
//        daoAuthenticationProvider.setPasswordEncoder(new Md5PasswordEncoder());
//        auth.authenticationProvider(daoAuthenticationProvider);
//    }


    /*
    *
    * 这里可以增加自定义的投票器
    */
    @SuppressWarnings("rawtypes")
    @Bean(name = "accessDecisionManager")
    public AccessDecisionManager accessDecisionManager() {
        log.info("AccessDecisionManager");
        List<AccessDecisionVoter<?>> decisionVoters = new ArrayList<>();
        decisionVoters.add(new RoleVoter());
        decisionVoters.add(new AuthenticatedVoter());
        decisionVoters.add(webExpressionVoter());// 启用表达式投票器
        return  new AffirmativeBased(decisionVoters);
    }

    /*
     * 表达式控制器
     */
    @Bean(name = "expressionHandler")
    public DefaultWebSecurityExpressionHandler webSecurityExpressionHandler() {
        log.info("DefaultWebSecurityExpressionHandler");
        return new DefaultWebSecurityExpressionHandler();
    }

    /*
     * 表达式投票器
     */
    @Bean(name = "expressionVoter")
    public WebExpressionVoter webExpressionVoter() {
        log.info("WebExpressionVoter");
        WebExpressionVoter webExpressionVoter = new WebExpressionVoter();
        webExpressionVoter.setExpressionHandler(webSecurityExpressionHandler());
        return webExpressionVoter;
    }


    /**
     * RSA加密解密
     * @return
     */
    @Bean
    public RsaSecretEncryptor rsaSecretEncryptor() {
        KeyStoreKeyFactory keyStoreKeyFactory = new KeyStoreKeyFactory(
                new ClassPathResource("tuicr.server.keystore"),
                keyStorePwd.toCharArray()
        );
        return new RsaSecretEncryptor(
                keyStoreKeyFactory.getKeyPair(keyStoreAlias)
        );
    }
}
