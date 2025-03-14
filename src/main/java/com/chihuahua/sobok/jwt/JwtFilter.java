package com.chihuahua.sobok.jwt;

import com.chihuahua.sobok.member.MyUserDetailsService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;


public class JwtFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        Cookie[] cookies = request.getCookies();
        if(cookies == null) {
            filterChain.doFilter(request, response);
            return; // 쿠키가 비어있으면 다음 필터로 패스
        }
        var jwtCookie = "";
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals("accessToken")) {
                jwtCookie = cookie.getValue();
            }
        }

        Claims claim;
        try {
            claim = JwtUtil.extractToken(jwtCookie);

        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        var arr = claim.get("authorities").toString().split(",");
        var authorities = Arrays.stream(arr)
                .map(SimpleGrantedAuthority::new).toList();

        var customUser = new MyUserDetailsService.CustomUser(
                claim.get("username").toString(),
                "none",
                authorities
        );
        var authToken = new UsernamePasswordAuthenticationToken(
                customUser, ""
        );
        authToken.setDetails(new WebAuthenticationDetailsSource()
                .buildDetails(request)
        );

        SecurityContextHolder.getContext().setAuthentication(authToken);

        filterChain.doFilter(request, response);
    }

}
