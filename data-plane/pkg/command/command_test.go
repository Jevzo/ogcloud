package command

import "testing"

func TestSubcommand(t *testing.T) {
	tests := []struct {
		name    string
		args    []string
		want    string
		wantErr string
	}{
		{"a known subcommand", []string{"load"}, "load", ""},
		{"none", nil, "", "usage: loader <load|agent>"},
		{"an unknown one", []string{"push"}, "", "usage: loader <load|agent>"},
		{"too many", []string{"load", "agent"}, "", "usage: loader <load|agent>"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			got, err := Subcommand("loader", test.args, "load", "agent")
			if got != test.want || (err == nil) != (test.wantErr == "") || err != nil && err.Error() != test.wantErr {
				t.Errorf("Subcommand(%v) = %q, %v", test.args, got, err)
			}
		})
	}
}
