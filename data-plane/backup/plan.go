// Package backup copies template archives into the backup target and back (D-0166). Archives are immutable
// versions (D-0042), so a key that exists with the same size is already copied.
package backup

import (
	"sort"
	"time"
)

// Object is one stored archive.
type Object struct {
	Key      string
	Size     int64
	Modified time.Time
}

// Plan is one run: the keys to copy, and the copies to delete.
type Plan struct {
	Copy  []string
	Prune []string
}

// PlanBackup copies what the target lacks and prunes copies whose source is gone once they are older than
// retention, so a deleted template stays restorable for at least that long (D-0043).
func PlanBackup(source, target []Object, now time.Time, retention time.Duration) Plan {
	plan := Plan{Copy: missing(source, target)}
	kept := byKey(source)
	for _, copied := range target {
		if _, ok := kept[copied.Key]; !ok && now.Sub(copied.Modified) > retention {
			plan.Prune = append(plan.Prune, copied.Key)
		}
	}
	sort.Strings(plan.Prune)
	return plan
}

// PlanRestore copies back what the data bucket lacks and deletes nothing.
func PlanRestore(backup, data []Object) Plan {
	return Plan{Copy: missing(backup, data)}
}

func missing(from, to []Object) []string {
	present := byKey(to)
	var keys []string
	for _, object := range from {
		if copied, ok := present[object.Key]; !ok || copied.Size != object.Size {
			keys = append(keys, object.Key)
		}
	}
	sort.Strings(keys)
	return keys
}

func byKey(objects []Object) map[string]Object {
	index := make(map[string]Object, len(objects))
	for _, object := range objects {
		index[object.Key] = object
	}
	return index
}
