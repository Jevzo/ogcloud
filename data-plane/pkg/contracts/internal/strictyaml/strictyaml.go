// Package strictyaml decodes contract files and rejects unknown fields, so typos fail loudly.
package strictyaml

import (
	"bytes"
	"fmt"

	"go.yaml.in/yaml/v3"
)

// Decode unmarshals data into out, failing on any field out does not declare.
func Decode(data []byte, out any) error {
	decoder := yaml.NewDecoder(bytes.NewReader(data))
	decoder.KnownFields(true)
	if err := decoder.Decode(out); err != nil {
		return fmt.Errorf("decode: %w", err)
	}
	return nil
}
