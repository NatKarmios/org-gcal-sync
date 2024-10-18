***Note: I don't use org-mode anymore, and have since switched to Obsidian, so I no longer use this project and will not continue to maintain it. Forks are welcome!***

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

## Event colours

<table>
<thead>
  <td>ID</td>
  <td>Background</td>
  <td>Foreground</td>
  <td>Example</td>
</thead>
<tbody>
  <tr>
    <td>1</td>
    <td>#a4bdfc</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #a4bdfc">Example</td>
  </tr>
  <tr>
    <td>2</td>
    <td>#7ae7bf</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #7ae7bf">Example</td>
  </tr>
  <tr>
    <td>3</td>
    <td>#dbadff</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #dbadff">Example</td>
  </tr>
  <tr>
    <td>4</td>
    <td>#ff887c</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #ff887c">Example</td>
  </tr>
  <tr>
    <td>5</td>
    <td>#fbd75b</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #fbd75b">Example</td>
  </tr>
  <tr>
    <td>6</td>
    <td>#ffb878</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #ffb878">Example</td>
  </tr>
  <tr>
    <td>7</td>
    <td>#46d6db</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #46d6db">Example</td>
  </tr>
  <tr>
    <td>8</td>
    <td>#e1e1e1</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #e1e1e1">Example</td>
  </tr>
  <tr>
    <td>9</td>
    <td>#5484ed</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #5484ed">Example</td>
  </tr>
  <tr>
    <td>10</td>
    <td>#51b749</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #51b749">Example</td>
  </tr>
  <tr>
    <td>11</td>
    <td>#dc2127</td>
    <td>#1d1d1d</td>
    <td style="color: #1d1d1d; background-color: #dc2127">Example</td>
  </tr>
</tbody>
</table>

---

Note that the org-mode parser used here shares that of orgzly; this is to ensure as
much compatibility with orgzly, as that's what I use on a day-to-day basis.


Many thanks to [Nevenz](https://github.com/nevenz) and the contributors to
[org-java](https://github.com/orgzly/org-java) and
[orgzly](https://github.com/orgzly/orgzly-android).
