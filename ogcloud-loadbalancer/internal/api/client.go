package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/ogwars/ogcloud-loadbalancer/internal/kafka"
)

const fetchTimeout = 5 * time.Second

const (
	loginPath           = "/api/v1/auth/login"
	networkSettingsPath = "/api/v1/network"
)

type authTokenResponse struct {
	AccessToken string `json:"accessToken"`
}

func FetchNetworkSettings(apiURL string, apiEmail string, apiPassword string) (*kafka.APINetworkResponse, error) {
	client := &http.Client{Timeout: fetchTimeout}

	accessToken, err := login(client, apiURL, apiEmail, apiPassword)
	if err != nil {
		return nil, err
	}

	networkSettingsURL := apiURL + networkSettingsPath
	req, err := http.NewRequest(http.MethodGet, networkSettingsURL, nil)
	if err != nil {
		return nil, fmt.Errorf("build request for %s: %w", networkSettingsURL, err)
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)

	resp, err := client.Do(req)
	if err != nil {
		return nil, fmt.Errorf("GET %s: %w", networkSettingsURL, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("unexpected status %d from %s", resp.StatusCode, networkSettingsURL)
	}

	var settings kafka.APINetworkResponse
	if err := json.NewDecoder(resp.Body).Decode(&settings); err != nil {
		return nil, fmt.Errorf("decode network settings: %w", err)
	}

	return &settings, nil
}

func login(client *http.Client, apiURL string, apiEmail string, apiPassword string) (string, error) {
	if apiEmail == "" || apiPassword == "" {
		return "", fmt.Errorf("OGCLOUD_API_EMAIL and OGCLOUD_API_PASSWORD must be set")
	}

	body, err := json.Marshal(map[string]string{
		"email":    apiEmail,
		"password": apiPassword,
	})
	if err != nil {
		return "", fmt.Errorf("encode API login payload: %w", err)
	}

	loginURL := apiURL + loginPath
	req, err := http.NewRequest(http.MethodPost, loginURL, bytes.NewReader(body))
	if err != nil {
		return "", fmt.Errorf("build request for %s: %w", loginURL, err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		return "", fmt.Errorf("POST %s: %w", loginURL, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("unexpected status %d from %s", resp.StatusCode, loginURL)
	}

	var tokenResponse authTokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&tokenResponse); err != nil {
		return "", fmt.Errorf("decode auth response: %w", err)
	}
	if tokenResponse.AccessToken == "" {
		return "", fmt.Errorf("missing access token from %s", loginURL)
	}

	return tokenResponse.AccessToken, nil
}
