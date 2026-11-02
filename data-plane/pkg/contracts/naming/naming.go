// Package naming turns contract names such as serverId into Go (ServerId) and Kotlin (SERVER_ID)
// identifiers, the same way for every generator.
package naming

import (
	"strings"
	"unicode"
)

// Pascal capitalises the first letter: serverId becomes ServerId.
func Pascal(name string) string {
	if name == "" {
		return ""
	}
	runes := []rune(name)
	runes[0] = unicode.ToUpper(runes[0])
	return string(runes)
}

// UpperSnake splits at capitals: serverId becomes SERVER_ID.
func UpperSnake(name string) string {
	var b strings.Builder
	for i, r := range name {
		if unicode.IsUpper(r) && i > 0 {
			b.WriteByte('_')
		}
		b.WriteRune(unicode.ToUpper(r))
	}
	return b.String()
}
