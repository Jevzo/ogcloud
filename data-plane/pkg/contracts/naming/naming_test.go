package naming

import "testing"

func TestIdentifiers(t *testing.T) {
	tests := []struct{ in, pascal, snake string }{
		{"serverId", "ServerId", "SERVER_ID"},
		{"servers", "Servers", "SERVERS"},
		{"webSessionNamespace", "WebSessionNamespace", "WEB_SESSION_NAMESPACE"},
		{"", "", ""},
	}
	for _, tt := range tests {
		if got := Pascal(tt.in); got != tt.pascal {
			t.Errorf("Pascal(%q) = %q", tt.in, got)
		}
		if got := UpperSnake(tt.in); got != tt.snake {
			t.Errorf("UpperSnake(%q) = %q", tt.in, got)
		}
	}
}
