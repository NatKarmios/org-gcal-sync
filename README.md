# `org-gcal-sync`
*A one-way event synchronisation tool from org-mode to Google Calendar*

---

## Usage
```
Usage: org-gcal-sync options_list
Options: 
    --dry, -d [false] -> Skip sending event changes to Google 
    --autoRetry, -r [false] -> Automatically retry on certain errors 
    --config, -c [./config.yaml] -> Path to the desired config file { String }
    --verbose, -v -> Logging verbosity { Int }
    --help, -h -> Usage info
```

## Configuration
See [`config.example.yml`](./config.example.yaml).

`org-gcal-sync` requires Google API credentials; get these from your admin console.

---

Note that the org-mode parser used here shares that of orgzly; this is to ensure as
much compatibility with orgzly, as that's what I use on a day-to-day basis.


Many thanks to [Nevenz](https://github.com/nevenz) and the contributors to
[org-java](https://github.com/orgzly/org-java) and
[orgzly](https://github.com/orgzly/orgzly-android).