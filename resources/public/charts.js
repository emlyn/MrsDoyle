// Load the Visualization API and the piechart package.
google.load('visualization', '1.0', {'packages':['corechart', 'annotatedtimeline']});

// Set a callback to run when the Google Visualization API is loaded.
google.setOnLoadCallback(drawCharts);

function startOfDay(date) {
    var d = new Date(date);
    d.setHours(0);
    d.setMinutes(0);
    d.setSeconds(0);
    d.setMilliseconds(0);
    return d;
}

function endOfDay(date) {
    var d = new Date(date);
    d.setHours(23);
    d.setMinutes(59);
    d.setSeconds(59);
    d.setMilliseconds(999);
    return d;
}

// Add data points to an array to display a barchart in an annotatedTimeline plot.
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
    points.push([date].concat(zeros));
    var dt2 = endOfDay(date);
    for (i in vals) {
        if (vals[i] != 0) {
            points.push([date].concat(vals));
            points.push([dt2].concat(vals));
            break;
        }
    }
    points.push([dt2].concat(zeros));
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
