package backup

import (
	"reflect"
	"testing"
	"time"
)

func TestPlans(t *testing.T) {
	now := time.Date(2026, 11, 9, 3, 30, 0, 0, time.UTC)
	retention := 14 * 24 * time.Hour
	old := now.Add(-15 * 24 * time.Hour)
	recent := now.Add(-time.Hour)
	tests := []struct {
		name           string
		source, target []Object
		want           Plan
	}{
		{"an empty target gets everything", []Object{{"templates/b", 2, recent}, {"templates/a", 1, recent}}, nil,
			Plan{Copy: []string{"templates/a", "templates/b"}}},
		{"copied archives are left alone", []Object{{"templates/a", 1, recent}}, []Object{{"templates/a", 1, old}},
			Plan{}},
		{"a copy of another size is copied again", []Object{{"templates/a", 2, recent}}, []Object{{"templates/a", 1, recent}},
			Plan{Copy: []string{"templates/a"}}},
		{"a deleted template's copy stays within retention", nil, []Object{{"templates/a", 1, recent}},
			Plan{}},
		{"a deleted template's copy goes after retention", nil, []Object{{"templates/b", 1, old}, {"templates/a", 1, old}},
			Plan{Prune: []string{"templates/a", "templates/b"}}},
		{"a still existing template is never pruned", []Object{{"templates/a", 1, old}}, []Object{{"templates/a", 1, old}},
			Plan{}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			if got := PlanBackup(test.source, test.target, now, retention); !reflect.DeepEqual(got, test.want) {
				t.Errorf("PlanBackup = %+v, want %+v", got, test.want)
			}
		})
	}
}

func TestRestoreCopiesOnlyWhatIsMissing(t *testing.T) {
	backup := []Object{{Key: "templates/a", Size: 1}, {Key: "templates/b", Size: 1}}
	data := []Object{{Key: "templates/a", Size: 1}, {Key: "templates/c", Size: 1}}
	if got := PlanRestore(backup, data); !reflect.DeepEqual(got, Plan{Copy: []string{"templates/b"}}) {
		t.Errorf("PlanRestore = %+v", got)
	}
}
