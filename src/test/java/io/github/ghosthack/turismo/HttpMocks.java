package io.github.ghosthack.turismo;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HttpMocks {

    public static HttpServletRequest getRequestMock(String method, String path) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getPathInfo()).thenReturn(path);
        return req;
    }
    
    public static HttpServletResponse getResponseMock() {
        HttpServletResponse res = mock(HttpServletResponse.class);
        return res;
    }

}
