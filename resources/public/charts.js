// Load the Visualization API and the piechart package.
google.load('visualization', '1.0', {'packages':['corechart', 'annotatedtimeline']});

// Set a callback to run when the Google Visualization API is loaded.
google.setOnLoadCallback(drawCharts);

function dateAt(date, h, m, s, ms) {
    var d = new Date(date);
    d.setHours(h);
    d.setMinutes(m);
    d.setSeconds(s);
    d.setMilliseconds(ms);
    return d;
}

function startOfDay(date) {
    return dateAt(date, 0, 0, 0, 0);
}

function endOfDay(date) {
    return dateAt(date, 23, 59, 59, 999);
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

// Callback that creates and populates our charts
function drawCharts() {
    var options = {title: 'Who has drunk the most tea via Mrs Doyle (all time)?',
                   width: 1000,
                   height: 500};

    // Create the data table.
    var data = new google.visualization.DataTable();
    data.addColumn('string', 'Drinker');
    data.addColumn('number', 'Cups drunk');

    $.ajax({
        url: "/drinker-cups",
        dataType: "json",
        async: false
    }).done(function(json) {
        data.addRows(json);
    });

    // Instantiate and draw this chart, passing in some options.
    var all_time_drunk_chart = new google.visualization.PieChart(document.getElementById('all_time_drunk_div'));
    all_time_drunk_chart.draw(data, options);

    // ----------------------

    var options = {title: 'Who has made tea most times via Mrs Doyle (all time)?',
                   width: 1000,
                   height: 500};

    // Create the data table.
    var data = new google.visualization.DataTable();
    data.addColumn('string', 'Maker');
    data.addColumn('number', 'Rounds made');

    $.ajax({
        url: "/maker-rounds",
        dataType: "json",
        async: false
    }).done(function(json) {
        data.addRows(json);
    });

    // Instantiate and draw this chart, passing in some options.
    var all_time_rounds_chart = new google.visualization.PieChart(document.getElementById('all_time_rounds_div'));
    all_time_rounds_chart.draw(data, options);

    // ----------------------

    var options = {title: 'Who has initiated the most tea rounds via Mrs Doyle (all time)?',
                   width: 1000,
                   height: 500};

    // Create the data table.
    var data = new google.visualization.DataTable();
    data.addColumn('string', 'Initiator');
    data.addColumn('number', 'Rounds initiated');

    $.ajax({
        url: "/initiator-rounds",
        dataType: "json",
        async: false
    }).done(function(json) {
        data.addRows(json);
    });

    // Instantiate and draw this chart, passing in some options.
    var all_time_initiated_chart = new google.visualization.PieChart(document.getElementById('all_time_initiated_div'));
    all_time_initiated_chart.draw(data, options);

    // ----------------------

    var options = {title: 'Who has been luckiest so far (and so is more likely to get picked next)?',
                   width: 1000,
                   height: 500,
                   interpolateNulls: true,
                   series: {
                       0: {type: 'bars'},
                       1: {type: 'line', visibleInLegend: false}},
                   vAxis: {minValue: 0},
                   hAxis: {viewWindow: {min: 1}}};

    // Create the data table.
    data = new google.visualization.DataTable();
    data.addColumn('string', 'Drinker');
    data.addColumn('number', 'Relative probability');
    data.addColumn('number', 'Fixed height line');

    $.ajax({
        url: "/drinker-luck",
        dataType: "json",
        async: false
    }).done(function(json) {
        for (var i in json) {
            json[i].push(null)
        }
        json.unshift([null, null, 1]);
        json.push([null, null, 1]);
        options.hAxis.viewWindow.max = json.length - 1
        data.addRows(json);
    });

    // Instantiate and draw this chart, passing in some options.
    var luckiest_chart = new google.visualization.ComboChart(document.getElementById('luckiest_so_far_div'));
    luckiest_chart.draw(data, options);

    // ----------------------

    var options = {title: 'How many cups of tea have people had to make per round?',
                   width: 1000,
                   height: 400,
                   hAxis: {viewWindow: {min: 1.5},
                           gridlines: {}}};

    // Create the data table.
    data = new google.visualization.DataTable();
    data.addColumn('number', 'Round size');
    data.addColumn('number', 'Frequency');

    $.ajax({
        url: "/round-sizes",
        dataType: "json",
        async: false
    }).done(function(json) {
        data.addRows(json);
        options.hAxis.viewWindow.max = json[json.length-1][0] + 0.5;
        options.hAxis.gridlines.count = json.length;
    });

    // Instantiate and draw this chart, passing in some options.
    var rounds_chart = new google.visualization.ColumnChart(document.getElementById('round_sizes_div'));
    rounds_chart.draw(data, options);

    // ----------------------

    var options = {title: 'How does the activity vary depending on day of week?',
                   width: 1000,
                   height: 400,
                   series: [{color: 'red'},
                            {color: 'red', visibleInLegend: false},
                            {color: 'blue'},
                            {color: 'blue', visibleInLegend: false}]};

    // Create the data table.
    data = new google.visualization.DataTable();
    data.addColumn('string', 'Day');
    data.addColumn('number', 'Cups');
    data.addColumn({type: 'number', role: 'interval'});
    data.addColumn({type: 'number', role: 'interval'});
    data.addColumn('number', 'Max Cups');
    data.addColumn({type: 'boolean', role: 'certainty'}); // for dashed lines
    data.addColumn('number', 'Rounds');
    data.addColumn({type: 'number', role: 'interval'});
    data.addColumn({type: 'number', role: 'interval'});
    data.addColumn('number', 'Max Cups');
    data.addColumn({type: 'boolean', role: 'certainty'}); // for dashed lines

    $.ajax({
        url: "/weekly-stats",
        dataType: "json",
        async: false
    }).done(function(json) {
        data.addRows(json.map(function(d){
            var c = d.cups;
            var r = d.rounds;
            return [d.day,
                    c.mean,
                    c.mean - c.std,
                    c.mean + c.std,
                    c.max,
                    false,
                    r.mean,
                    r.mean - r.std,
                    r.mean + r.std,
                    r.max,
                    false];
        }));
    });

    // Instantiate and draw this chart, passing in some options.
    var weekly_chart = new google.visualization.LineChart(document.getElementById('weekly_stats_div'));
    weekly_chart.draw(data, options);

    // --------------------

    var options = {width: 1000,
                   height: 400,
                   fill: 50,
                   min: 0,
                   dateFormat: 'EEE d MMM yy'};

    // Create the data table.
    var data = new google.visualization.DataTable();
    data.addColumn('date', 'Date');
    data.addColumn('number', 'Rounds');
    data.addColumn('number', 'Cups');

    $.ajax({
        url: "/recent-drinkers",
        dataType: "json",
        async: false
    }).done(function(json) {
        var lastDate = null;
        var points = []
        for (i in json) {
            var d = json[i][0];
            var date = new Date(d[0], d[1]-1, d[2], 0, 0, 0, 0);
            var vals = json[i].slice(1);
            addPoints(points, date, vals);
        }
        data.addRows(points);
    });

    // Instantiate and draw this chart, passing in some options.
    var all_time_drunk_chart = new google.visualization.AnnotatedTimeLine(document.getElementById('last_week_drunk_div'));
    all_time_drunk_chart.draw(data, options);
}
