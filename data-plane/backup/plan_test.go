package backup

import (
	"errors"
	"reflect"
	"testing"
	"time"
)

func TestPlanBackup(t *testing.T) {
	now := time.Date(2026, 11, 9, 3, 30, 0, 0, time.UTC)
	retention := 14 * 24 * time.Hour
	old := now.Add(-15 * 24 * time.Hour)
	recent := now.Add(-time.Hour)
	tests := []struct {
		name                  string
		source, target, marks []Object
		want                  Plan
	}{
		{"an empty target gets everything", []Object{{"templates/b", 2, recent}, {"templates/a", 1, recent}}, nil, nil,
			Plan{Copy: []string{"templates/a", "templates/b"}}},
		{"copied archives are left alone", []Object{{"templates/a", 1, recent}}, []Object{{"templates/a", 1, old}}, nil,
			Plan{}},
		{"a copy of another size is copied again", []Object{{"templates/a", 2, recent}}, []Object{{"templates/a", 1, recent}}, nil,
			Plan{Copy: []string{"templates/a"}}},
		{"an old copy whose source just went is marked, not pruned",
			[]Object{{"templates/a", 1, recent}}, []Object{{"templates/a", 1, old}, {"templates/b", 1, old}}, nil,
			Plan{Mark: []string{"templates/b"}}},
		{"a copy stays while its mark is within retention",
			[]Object{{"templates/a", 1, recent}}, []Object{{"templates/a", 1, old}, {"templates/b", 1, old}},
			[]Object{{"templates/b", 0, recent}},
			Plan{}},
		{"a copy goes with its mark once the mark is past retention",
			[]Object{{"templates/a", 1, recent}}, []Object{{"templates/a", 1, old}, {"templates/c", 1, old}, {"templates/b", 1, old}},
			[]Object{{"templates/c", 0, old}, {"templates/b", 0, old}},
			Plan{Prune: []string{"templates/b", "templates/c"}}},
		{"a mark is dropped when its source comes back",
			[]Object{{"templates/a", 1, recent}}, []Object{{"templates/a", 1, old}}, []Object{{"templates/a", 0, old}},
			Plan{Unmark: []string{"templates/a"}}},
		{"a mark without its copy is dropped",
			[]Object{{"templates/a", 1, recent}}, []Object{{"templates/a", 1, old}}, []Object{{"templates/z", 0, old}},
			Plan{Unmark: []string{"templates/z"}}},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			got, err := PlanBackup(test.source, test.target, test.marks, now, retention)
			if err != nil {
				t.Fatalf("PlanBackup: %v", err)
			}
			if !reflect.DeepEqual(got, test.want) {
				t.Errorf("PlanBackup = %+v, want %+v", got, test.want)
			}
		})
	}
}

// An emptied or lost data bucket is what backups exist for, so it must never prune them (D-0186).
func TestPlanBackupRefusesASourceWithoutAnyCopiedArchive(t *testing.T) {
	now := time.Date(2026, 11, 9, 3, 30, 0, 0, time.UTC)
	old := now.Add(-30 * 24 * time.Hour)
	copies := []Object{{"templates/a", 1, old}, {"templates/b", 1, old}}
	marks := []Object{{"templates/a", 0, old}, {"templates/b", 0, old}}
	for name, source := range map[string][]Object{
		"an empty source":           nil,
		"a source of other objects": {{"templates/x", 1, now}},
	} {
		t.Run(name, func(t *testing.T) {
			if plan, err := PlanBackup(source, copies, marks, now, 14*24*time.Hour); !errors.Is(err, ErrSourceLost) {
				t.Errorf("PlanBackup = %+v, %v; want ErrSourceLost", plan, err)
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
