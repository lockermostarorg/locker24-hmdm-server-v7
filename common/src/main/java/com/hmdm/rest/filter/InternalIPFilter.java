/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.rest.filter;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.inject.Named;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Singleton
public class InternalIPFilter implements Filter {

    private final BaseIPFilter extra;

    public InternalIPFilter() {
        this.extra = null;
    }

    @Inject
    public InternalIPFilter(@Named("internal.source.ip") String sourceIp) {
        this.extra = sourceIp == null || sourceIp.isBlank() ? null : new BaseIPFilter(sourceIp.trim(), "", "");
    }

    public void init(FilterConfig filterConfig) throws ServletException {
    }

    public void destroy() {
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (!isAllowed(servletRequest.getRemoteAddr())) {
            ((HttpServletResponse)servletResponse).sendError(403);
            return;
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    public boolean isAllowed(String remoteAddr) {
        if (remoteAddr == null || remoteAddr.isBlank() || !isLiteral(remoteAddr)) {
            return false;
        }
        try {
            if (InetAddress.getByName(remoteAddr).isLoopbackAddress()) {
                return true;
            }
        } catch (UnknownHostException e) {
            return false;
        }
        return extra != null && extra.match(remoteAddr);
    }

    private static boolean isLiteral(String addr) {
        if (addr.indexOf(':') >= 0) {
            return true;
        }
        for (int i = 0; i < addr.length(); i++) {
            char c = addr.charAt(i);
            if (!(c >= '0' && c <= '9') && c != '.') {
                return false;
            }
        }
        return true;
    }
}
