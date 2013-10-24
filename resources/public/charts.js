// Load the Visualization API and the piechart package.
google.load('visualization', '1.0', {'packages':['corechart', 'annotatedtimeline']});

// Set a callback to run when the Google Visualization API is loaded.
google.setOnLoadCallback(drawCharts);

function dateAt(date, h, m, s, ms) {
    var d = new Date(date);
    d.setHours(h);
    d.setMinutes(m == undefined ? 0 : m);
    d.setSeconds(s == undefined ? 0 : s);
    d.setMilliseconds(ms == undefined ? 0 : ms);
    return d;
}

function startOfDay(date) {
    return dateAt(date, 0);
}

function endOfDay(date) {
    return dateAt(date, 23, 59, 59);
}

// Massage data to make annotatedTimeline plot into cityscape plot.
function addPoints(points, date, vals) {
    var zeros = vals.map(function (v) {return 0;});
    if (points.length > 0) {
        var dt = startOfDay(points[points.length - 1][0]);
        dt.setDate(dt.getDate() + 1);
        while (dt < date) {
            points.push([startOfDay(dt)].concat(zeros));
            points.push([endOfDay(dt)].concat(zeros));
            dt.setDate(dt.getDate() + 1);
        }
    }

    points.push([date].concat(vals));
    points.push([endOfDay(date)].concat(vals));
}

function drawChart(url, options, columns, func, chart) {
    var data = new google.visualization.DataTable();
    for (i in columns) {
        data.addColumn(columns[i]);
    }
    $.ajax({
        url: url,
        dataType: 'json',
        async: true
    }).done(function(json){
        if (func != null) {
            json = func(json, options);
        }
        data.addRows(json);
        chart.draw(data, options);
    });
}

function top10(json) {
    var tail = json.slice(10);
    if (tail.length <= 1) return json;
    var others = tail.reduce(function(a, b) {
        var result = ['Others'];
        for (var i in a) {
            if (i > 0) result.push(a[i] + b[i]);
        }
        return result;
    });
    return json.slice(0,10).concat([others]);
}

// Callback that creates and populates our charts
function drawCharts() {
    drawChart('/drinker-cups',
              {title: 'Who has drunk the most tea via Mrs Doyle (all time)?'},
              [{type: 'string', label: 'Drinker'},
               {type: 'number', label: 'Cups drunk'}],
              top10,
              new google.visualization.PieChart(
                  document.getElementById('all_time_drunk_div')));

    drawChart('/initiator-rounds',
              {title: 'Who has initiated the most tea rounds via Mrs Doyle (all time)?'},
              [{type: 'string', label: 'Initiator'},
               {type: 'number', label: 'Rounds initiated'}],
              top10,
              new google.visualization.PieChart(
                  document.getElementById('all_time_initiated_div')));

    drawChart('/drinker-daily-cups',
              {title: 'Who drink the most cups of tea per day?',
               vAxis: {gridlines: {}}},
              [{type: 'string', label: 'Name'},
               {type: 'number', label: 'Mean'},
               {type: 'number', label: 'Max'}],
              function(json, options) {
                  var n = Math.max.apply(null, json.map(function(x) {return x[2];}));
                  options.vAxis.gridlines.count = n + 1;
                  return json.slice(0,20);
              },
              new google.visualization.ColumnChart(
                  document.getElementById('daily_cups_div')));

    drawChart('/drinker-luck',
              {title: 'Who has been luckiest so far (and so is more likely to get picked next)?',
               interpolateNulls: true,
               series: {0: {type: 'bars'},
                        1: {type: 'line', visibleInLegend: false}},
               vAxis: {minValue: 0},
               hAxis: {viewWindow: {min: 1}}},
              [{type: 'string', label: 'Drinker'},
               {type: 'number', label: 'Relative probability'},
               {type: 'number', label: 'Fixed-height line'}],
              function(json, options) {
                  for (var i in json) {
                      json[i].push(null);
                  }
                  json.unshift([null, null, 1]);
                  json.push([null, null, 1]);
                  options.hAxis.viewWindow.max = json.length - 1;
                  return json;
              },
              new google.visualization.ComboChart(
                  document.getElementById('luckiest_so_far_div')));

    drawChart('/round-sizes',
              {title: 'How many cups of tea have people had to make per round?',
               hAxis: {viewWindow: {min: 1.5},
                       gridlines: {}},
               vAxis: {logScale: true,
                       baseline: 0.1,
                       minValue: 0.1,
                       ticks: [{v: 0.1, f: "0"},
                               {v: 1, f: "1"},
                               {v: 10, f: "10"},
                               {v: 100, f: "100"},
                               {v: 1000, f: "1000"}]}},
              [{type: 'number', label: 'Round size'},
               {type: 'number', label: 'Frequency'}],
              function(json, options) {
                  var max = json[json.length-1][0];
                  var min = json[0][0];
                  options.hAxis.viewWindow.max = max + 0.5;
                  options.hAxis.gridlines.count = max - min + 1;
                  return json;
              },
              new google.visualization.ColumnChart(
                  document.getElementById('round_sizes_div')));

    drawChart('/weekly-stats',
              {title: 'How does the activity vary depending on day of week?',
               series: [{color: 'blue'},
                        {color: 'red'}]},
              [{type: 'string', label: 'Day'},
               {type: 'number', label: 'Mean +/- Std. Dev.'},
               {type: 'number', role: 'interval'},
               {type: 'number', role: 'interval'},
               {type: 'number', label: 'Max'},
               {type: 'boolean', role: 'certainty'}], // for dashed lines
              function(json) {
                  return json.map(function(d){
                      var c = d.cups;
                      var r = d.rounds;
                      return [d.day,
                              c.mean,
                              c.mean - c.std,
                              c.mean + c.std,
                              c.max,
                              false];
                  });
              },
              new google.visualization.LineChart(
                  document.getElementById('weekly_stats_div')));

    drawChart('/recent-drinkers',
              {fill: 50,
               min: 0,
               dateFormat: 'EEE d MMM yy'},
              [{type: 'date', label: 'Date'},
               {type: 'number', label: 'Rounds'},
               {type: 'number', label: 'Cups'}],
              function(json) {
                  var lastDate = null;
                  var points = [];
                  for (i in json) {
                      var d = json[i][0];
                      var date = new Date(d[0], d[1]-1, d[2], 0, 0, 0, 0);
                      var vals = json[i].slice(1);
                      addPoints(points, date, vals);
                  }
                  return points;
              },
              new google.visualization.AnnotatedTimeLine(
                  document.getElementById('activity_timeline_div')));

}
