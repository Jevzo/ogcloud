package deploytest

import (
	"strings"
	"testing"
)

// values.schema.json refuses what would otherwise install silently wrong.
func TestTheSchemaRefusesValuesOperatorsGotWrong(t *testing.T) {
	for name, test := range map[string]struct {
		args []string
		want string
	}{
		// The owner must choose (D-0046); the chart has no default.
		"no EULA choice": {[]string{"--set", "publicOrigin=https://cloud.example.com"}, "eulaAccepted"},
		"a mistyped key": {[]string{"--set", "monitring.enabled=true"}, "monitring"},
		"an origin with a path": {
			[]string{"--set", "publicOrigin=https://cloud.example.com/app", "--set", "minecraft.eulaAccepted=true"}, "publicOrigin",
		},
	} {
		t.Run(name, func(t *testing.T) {
			_, err := helmTemplate(t, nil, test.args...)
			if err == nil || !strings.Contains(err.Error(), test.want) {
				t.Errorf("rendered, or failed without naming %s: %v", test.want, err)
			}
		})
	}
}
