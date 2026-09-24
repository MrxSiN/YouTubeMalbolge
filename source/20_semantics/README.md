# Semantic runtime source area

Contains the Premium offer and video-ad Semantic Endpoint records. More Endpoint
types and effect pipelines remain open.

Both units execute at build time through the bounded Malbolge evaluator.

`endpoint_sponsorblock_player.mal` declares the SponsorBlock player Endpoints. The
`player_seek` Endpoint is a `CALL` Endpoint: bound and resolved cold, never hooked.

`endpoint_settings_entry.mal` and `endpoint_preference_api.mal` declare the settings
screen builder Endpoint and the preference CALL Endpoints used to add the entry.
