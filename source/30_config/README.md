# Configuration source area

Contains the Premium promotion and video-ad boolean ConfigItem records. The
generated runtime publishes immutable ConfigSnapshot arrays and listens to
RemotePreferences changes.

Configuration manager UI and migration remain open.

`config_sponsorblock.mal` declares the `sponsorblock` storage group: a master switch
and one auto-skip switch per SponsorBlock category.
